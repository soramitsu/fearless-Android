package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.math.BigInteger

/**
 * A `polkadot_signTransaction` payload (polkadot.js `SignerPayloadJSON`), read once for both the preview and the
 * signature: the call the preview decodes is exactly the `method` bytes that are signed.
 *
 * Every field is read strictly as the type it stands for. Hashes are exactly 32 bytes, the era is one valid era
 * encoding and numbers fit their integer types, so no field can carry bytes that belong to another one. The signed
 * bytes are then encoded from these values by [PolkadotSigningPayload], never by joining the dApp's hex strings.
 */
internal class PolkadotSignPayload private constructor(
    /** The account the payload names, or null when it names none. */
    val address: String?,
    val genesisHash: ByteArray,
    /** The block the era counts from (the genesis hash for an immortal era). */
    val blockHash: ByteArray,
    /** The SCALE-encoded call. */
    val method: ByteArray,
    /** One valid era encoding: `00` for an immortal era, two bytes for a mortal one. */
    val era: ByteArray,
    val nonce: BigInteger,
    /** The tip in planks of the native token. */
    val tip: BigInteger,
    val specVersion: Long,
    val transactionVersion: Long,
    /** What the dApp says about the extrinsic format; [PolkadotSigningPayload] checks it against the runtime. */
    val format: PolkadotPayloadFormat
) {
    /** Whether the transaction never expires: an immortal era is the single byte 0x00. */
    val isImmortal: Boolean
        get() = era.size == 1

    companion object {
        private const val HASH_SIZE = 32
        private const val U32_SIZE = 4
        private const val U64_SIZE = 8
        private const val U128_SIZE = 16
        private const val MORTAL_ERA_SIZE = 2
        private const val MIN_MORTAL_PERIOD = 4
        private const val ERA_PERIOD_BITS = 0xf
        private const val ERA_PHASE_SHIFT = 4
        private const val ERA_QUANTIZE_SHIFT = 12
        private const val BYTE_MASK = 0xff
        private const val BYTE_BITS = 8

        // A call above this size is refused rather than decoded: no wallet-reviewed request comes close to it.
        private const val MAX_METHOD_SIZE = 128 * 1024
        private const val MAX_SIGNED_EXTENSIONS = 64
        private const val MAX_NAME_LENGTH = 128
        private val HEX = Regex("0x(?:[0-9a-fA-F]{2})*")

        /** Reads a dApp payload; one that cannot be read exactly is refused, never guessed. */
        fun parse(message: String): PolkadotSignPayload {
            val payload = runCatching { message.toJsonObject() }.getOrNull()
                ?: rejectRequest("The transaction payload is malformed")
            val address = payload.optional("address")?.let { element ->
                element.stringValue() ?: rejectRequest("The transaction payload is malformed")
            }
            val era = payload.hex("era", 1, MORTAL_ERA_SIZE)
            requireRequest(isValidEra(era)) { "The transaction era is malformed" }
            val metadataHashMode = payload.optional("mode")?.let { element ->
                (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it == 0 || it == 1 }
                    ?: rejectRequest("The metadata hash mode is malformed")
            }

            return PolkadotSignPayload(
                address = address,
                genesisHash = payload.hex("genesisHash", HASH_SIZE, HASH_SIZE),
                blockHash = payload.hex("blockHash", HASH_SIZE, HASH_SIZE),
                method = payload.hex("method", 2, MAX_METHOD_SIZE),
                era = era,
                nonce = payload.unsigned("nonce", U64_SIZE),
                tip = payload.unsigned("tip", U128_SIZE),
                specVersion = payload.unsigned("specVersion", U32_SIZE).toLong(),
                transactionVersion = payload.unsigned("transactionVersion", U32_SIZE).toLong(),
                format = PolkadotPayloadFormat(
                    version = payload.optional("version")?.let { element ->
                        (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                            ?: rejectRequest("The extrinsic version is malformed")
                    },
                    signedExtensions = payload.optional("signedExtensions")?.let(::signedExtensionNames),
                    metadataHashMode = metadataHashMode,
                    metadataHash = payload.optional("metadataHash")?.let { payload.hex("metadataHash", HASH_SIZE, HASH_SIZE) },
                    namesFeeAsset = payload.optional("assetId") != null,
                    appId = payload.optional("appId")?.let(::appId)
                )
            )
        }

        /**
         * Whether [era] is one era encoding as Substrate decodes it: `00` (immortal), or two little-endian bytes
         * whose period is a power of two of at least 4 and whose phase is below it.
         */
        fun isValidEra(era: ByteArray): Boolean = when (era.size) {
            1 -> era[0] == 0.toByte()
            MORTAL_ERA_SIZE -> {
                val encoded = era[0].toInt() and BYTE_MASK or (era[1].toInt() and BYTE_MASK shl BYTE_BITS)
                val period = 2 shl (encoded and ERA_PERIOD_BITS)
                val quantizeFactor = maxOf(period shr ERA_QUANTIZE_SHIFT, 1)
                val phase = (encoded shr ERA_PHASE_SHIFT) * quantizeFactor
                period >= MIN_MORTAL_PERIOD && phase < period
            }
            else -> false
        }

        // A field that is absent or JSON null; polkadot.js writes null for an Option that is None.
        private fun JsonObject.optional(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

        private fun JsonObject.hex(
            key: String,
            minSize: Int,
            maxSize: Int
        ): ByteArray {
            val text = this[key].stringValue()?.takeIf(HEX::matches)
                ?: rejectRequest("The transaction payload field $key is malformed")
            val bytes = text.fromHex()
            requireRequest(bytes.size in minSize..maxSize) { "The transaction payload field $key has the wrong size" }
            return bytes
        }

        // polkadot.js writes numbers as big-endian hex of their type's width, for example u32 as 0x00000004.
        private fun JsonObject.unsigned(key: String, maxSize: Int): BigInteger = BigInteger(1, hex(key, 1, maxSize))

        private fun signedExtensionNames(element: JsonElement): List<String> {
            val names = (element as? JsonArray)?.map { it.stringValue() }.orEmpty()
            requireRequest(element is JsonArray && names.size <= MAX_SIGNED_EXTENSIONS) { "The signed extensions are malformed" }
            return names.map { name ->
                name?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH } ?: rejectRequest("The signed extensions are malformed")
            }
        }

        private fun appId(element: JsonElement): BigInteger {
            val primitive = element as? JsonPrimitive ?: rejectRequest("The application id is malformed")
            val value = if (primitive.isString) {
                primitive.content.takeIf(HEX::matches)?.fromHex()
                    ?.takeIf { it.size in 1..U32_SIZE }?.let { BigInteger(1, it) }
            } else {
                primitive.content.toBigIntegerOrNull()
            }
            return value?.takeIf { it.signum() >= 0 && it.bitLength() <= U32_SIZE * BYTE_BITS }
                ?: rejectRequest("The application id is malformed")
        }
    }
}

/** The parts of a payload that describe its extrinsic format rather than the transaction. */
internal class PolkadotPayloadFormat(
    /** The extrinsic version the dApp built for, or null when it names none. */
    val version: Int?,
    /** The signed extensions the dApp built for, or null when it lists none. */
    val signedExtensions: List<String>?,
    /** The CheckMetadataHash mode (0 disabled, 1 enabled), or null when the dApp names none. */
    val metadataHashMode: Int?,
    /** The metadata hash the dApp commits to with mode 1. */
    val metadataHash: ByteArray?,
    /** Whether the dApp asks to pay the fee in an asset other than the native token. */
    val namesFeeAsset: Boolean,
    /** The CheckAppId application id, or null when the dApp names none. */
    val appId: BigInteger?
)
