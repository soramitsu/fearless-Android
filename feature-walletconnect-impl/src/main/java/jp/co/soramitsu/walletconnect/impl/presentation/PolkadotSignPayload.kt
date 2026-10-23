package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.common.utils.decodeToInt
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.scale.utils.directWrite
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * A `polkadot_signTransaction` payload, read once for both the preview and the signature: the
 * call the preview decodes is exactly the `method` bytes that are signed.
 */
internal class PolkadotSignPayload private constructor(
    /** The account the payload names, or null when it names none. */
    val address: String?,
    val genesisHash: ByteArray,
    /** The SCALE-encoded call. */
    val method: ByteArray,
    val tip: Int,
    private val blockHash: ByteArray,
    private val era: ByteArray,
    private val nonce: Int,
    private val specVersion: Int,
    private val transactionVersion: Int
) {
    /** Whether the transaction never expires: an immortal era is the single byte 0x00. */
    val isImmortal: Boolean
        get() = era.size == 1 && era[0] == 0.toByte()

    /** The payload bytes a signature commits to, in polkadot.js order. */
    fun signingPayload(): ByteArray = useScaleWriter {
        directWrite(method)
        directWrite(era)
        writeCompact(nonce)
        writeCompact(tip)
        writeUint32(specVersion)
        writeUint32(transactionVersion)
        directWrite(genesisHash)
        directWrite(blockHash)
    }

    companion object {
        /** Reads a dApp payload; one that cannot be read exactly is refused, never guessed. */
        fun parse(message: String): PolkadotSignPayload {
            val payload = runCatching { message.toJsonObject() }.getOrNull()
                ?: rejectRequest("The transaction payload is malformed")
            val address = payload["address"]?.takeIf { it !is JsonNull }?.let { element ->
                element.stringValue() ?: rejectRequest("The transaction payload is malformed")
            }

            return PolkadotSignPayload(
                address = address,
                genesisHash = payload.hexBytes("genesisHash"),
                method = payload.hexBytes("method"),
                tip = payload.hexBytes("tip").decodeToInt(),
                blockHash = payload.hexBytes("blockHash"),
                era = payload.hexBytes("era"),
                nonce = payload.hexBytes("nonce").decodeToInt(),
                specVersion = payload.hexBytes("specVersion").decodeToInt(),
                transactionVersion = payload.hexBytes("transactionVersion").decodeToInt()
            )
        }

        private fun JsonObject.hexBytes(key: String): ByteArray =
            this[key].stringValue()?.let { runCatching { it.fromHex() }.getOrNull() }
                ?: rejectRequest("The transaction payload is malformed")
    }
}
