package jp.co.soramitsu.walletconnect.impl.presentation

import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.scale.dataType.compactInt
import java.math.BigInteger

/**
 * The bytes a `polkadot_signTransaction` signature commits to: the call, then the explicit data of every signed
 * extension the runtime lists, then their implicit data, each encoded here from the parsed payload in the runtime's
 * order. A payload built for another runtime version or extrinsic format is refused, and so is a network with a
 * signed extension whose encoding the wallet does not know: the wallet never signs bytes it did not lay out itself.
 */
internal object PolkadotSigningPayload {
    private const val EXTRINSIC_VERSION = 4

    private const val CHECK_SPEC_VERSION = "CheckSpecVersion"
    private const val CHECK_TX_VERSION = "CheckTxVersion"
    private const val CHECK_GENESIS = "CheckGenesis"
    private const val CHECK_MORTALITY = "CheckMortality"
    private const val CHECK_NONCE = "CheckNonce"
    private const val CHARGE_TRANSACTION_PAYMENT = "ChargeTransactionPayment"
    private const val CHARGE_ASSET_TX_PAYMENT = "ChargeAssetTxPayment"
    private const val CHECK_METADATA_HASH = "CheckMetadataHash"
    private const val CHECK_APP_ID = "CheckAppId"

    // Extensions this object encodes explicit or implicit data for.
    private val ENCODED_EXTENSIONS = setOf(
        CHECK_SPEC_VERSION,
        CHECK_TX_VERSION,
        CHECK_GENESIS,
        CHECK_MORTALITY,
        CHECK_NONCE,
        CHARGE_TRANSACTION_PAYMENT,
        CHARGE_ASSET_TX_PAYMENT,
        CHECK_METADATA_HASH,
        CHECK_APP_ID
    )

    // Extensions with neither explicit nor implicit data. EthSetOrigin (Asset Hub) encodes as nothing too: the
    // Statemint parity vectors that polkadot.js rebuilt byte for byte list it.
    private val DATALESS_EXTENSIONS = setOf(
        "CheckNonZeroSender",
        "CheckWeight",
        "PrevalidateAttests",
        "AuthorizeCall",
        "StorageWeightReclaim",
        "WeightReclaim",
        "EthSetOrigin"
    )

    private const val METADATA_HASH_ENABLED = 1
    private const val OPTION_NONE: Byte = 0
    private const val OPTION_SOME: Byte = 1

    fun encode(payload: PolkadotSignPayload, runtime: RuntimeSnapshot): ByteArray {
        val extensions = runtime.metadata.extrinsic.signedExtensions
        requireFormat(payload.format, runtime, extensions)
        val (specVersion, transactionVersion) = runtime.runtimeVersion()
            ?: refuseTransaction(PolkadotRefusal.UnsupportedFormat, "the runtime version cannot be read")
        // The call is decoded with this runtime, so the signature must only be valid for this runtime version.
        requireTransaction(payload.specVersion == specVersion && payload.transactionVersion == transactionVersion, PolkadotRefusal.OtherRuntime)
        val explicit = useScaleWriter { extensions.forEach { writeExplicit(it, payload) } }
        val implicit = useScaleWriter { extensions.forEach { writeImplicit(it, payload) } }
        return payload.method + explicit + implicit
    }

    private fun requireFormat(
        format: PolkadotPayloadFormat,
        runtime: RuntimeSnapshot,
        extensions: List<String>
    ) {
        val unsupported = PolkadotRefusal.UnsupportedFormat
        requireTransaction(runtime.metadata.extrinsic.version.toInt() == EXTRINSIC_VERSION, unsupported) { "extrinsic version" }
        requireTransaction(format.version == null || format.version == EXTRINSIC_VERSION, unsupported) { "extrinsic version" }
        // A dApp that built for other extensions expects another payload: the signature would not match its extrinsic.
        requireTransaction(format.signedExtensions == null || format.signedExtensions == extensions, PolkadotRefusal.OtherRuntime)
        val unknown = extensions.firstOrNull { it !in DATALESS_EXTENSIONS && it !in ENCODED_EXTENSIONS }
        requireTransaction(unknown == null, unsupported) { "signed extension $unknown" }
        requireTransaction(!format.namesFeeAsset, unsupported) { "fee asset" }
        if (CHECK_METADATA_HASH in extensions) {
            val enabled = format.metadataHashMode == METADATA_HASH_ENABLED
            requireTransaction(enabled == (format.metadataHash != null), unsupported) { "metadata hash" }
        } else {
            requireTransaction(format.metadataHashMode != METADATA_HASH_ENABLED && format.metadataHash == null, unsupported) {
                "metadata hash"
            }
        }
        if (CHECK_APP_ID !in extensions) {
            requireTransaction(format.appId == null || format.appId.signum() == 0, unsupported) { "application id" }
        }
    }

    // Explicit data travels in the extrinsic.
    private fun ScaleCodecWriter.writeExplicit(extension: String, payload: PolkadotSignPayload) {
        when (extension) {
            CHECK_MORTALITY -> directWrite(payload.era, 0, payload.era.size)
            CHECK_NONCE -> compactInt.write(this, payload.nonce)
            CHARGE_TRANSACTION_PAYMENT -> compactInt.write(this, payload.tip)
            // The tip, then the fee asset: always None, the native token.
            CHARGE_ASSET_TX_PAYMENT -> {
                compactInt.write(this, payload.tip)
                writeByte(OPTION_NONE)
            }
            CHECK_METADATA_HASH -> writeByte(if (payload.format.metadataHash != null) OPTION_SOME else OPTION_NONE)
            CHECK_APP_ID -> compactInt.write(this, payload.format.appId ?: BigInteger.ZERO)
        }
    }

    // Implicit data is signed but not sent: the chain supplies its own values when it checks the signature.
    private fun ScaleCodecWriter.writeImplicit(extension: String, payload: PolkadotSignPayload) {
        when (extension) {
            CHECK_SPEC_VERSION -> writeUint32(payload.specVersion)
            CHECK_TX_VERSION -> writeUint32(payload.transactionVersion)
            CHECK_GENESIS -> directWrite(payload.genesisHash, 0, payload.genesisHash.size)
            CHECK_MORTALITY -> directWrite(payload.blockHash, 0, payload.blockHash.size)
            CHECK_METADATA_HASH -> {
                val hash = payload.format.metadataHash
                if (hash == null) {
                    writeByte(OPTION_NONE)
                } else {
                    writeByte(OPTION_SOME)
                    directWrite(hash, 0, hash.size)
                }
            }
        }
    }
}

/**
 * The spec and transaction versions of the runtime this metadata describes, read from its `System.Version`
 * constant. Null when the metadata has no readable version.
 */
internal fun RuntimeSnapshot.runtimeVersion(): Pair<Long, Long>? {
    val constant = metadata.moduleOrNull("System")?.constants?.get("Version") ?: return null
    val version = constant.type?.fromByteArrayOrNull(this, constant.value) as? Struct.Instance ?: return null
    val specVersion = (version.mapping["specVersion"] ?: version.mapping["spec_version"]) as? BigInteger
    val transactionVersion = (version.mapping["transactionVersion"] ?: version.mapping["transaction_version"]) as? BigInteger
    if (specVersion == null || transactionVersion == null) return null
    return specVersion.toLong() to transactionVersion.toLong()
}
