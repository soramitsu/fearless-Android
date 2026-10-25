package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.Compact
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import java.math.BigInteger

typealias ExtrinsicPayloadExtrasInstance = Map<String, Any?>

private const val _MORTALITY = "CheckMortality"
private const val _NONCE = "CheckNonce"
private const val _TIP = "ChargeTransactionPayment"
private const val _ASSET_TX_PAYMENT = "ChargeAssetTxPayment"
private const val _APP_ID = "CheckAppId"
private const val _CHECK_METADATA_HASH = "CheckMetadataHash"

// CheckMetadataHash: Mode::Disabled (0) in extras, Option::None (0) in additional. Must be written in pairs.
private val METADATA_HASH_DISABLED = BigInteger.ZERO
private val DEFAULT_APP_ID = BigInteger.ZERO

object SignedExtras : ExtrinsicPayloadExtras(
    name = "SignedExtras",
    extrasMapping = mapOf(
        _MORTALITY to EraType,
        _NONCE to Compact("Compact<Index>"),
        _TIP to Compact("Compact<u32>"),
        _APP_ID to Compact("Compact<u32>"),
        _ASSET_TX_PAYMENT to Tuple(_ASSET_TX_PAYMENT, listOf(TypeReference(Compact("u32")), TypeReference(Option("asset_id", TypeReference(u32))))),
        _CHECK_METADATA_HASH to u8
    ),
    defaultValues = mapOf(
        _APP_ID to DEFAULT_APP_ID,
        _CHECK_METADATA_HASH to METADATA_HASH_DISABLED
    )
) {
    const val ERA = _MORTALITY
    const val NONCE = _NONCE
    const val TIP = _TIP
    const val ASSET_TX_PAYMENT = _ASSET_TX_PAYMENT
    const val APP_ID = _APP_ID
    const val CHECK_METADATA_HASH = _CHECK_METADATA_HASH
}

private const val _GENESIS = "CheckGenesis"
private const val _SPEC_VERSION = "CheckSpecVersion"
private const val _TX_VERSION = "CheckTxVersion"

object AdditionalExtras : ExtrinsicPayloadExtras(
    name = "AdditionalExtras",
    extrasMapping = mapOf(
        _MORTALITY to H256,
        _GENESIS to H256,
        _SPEC_VERSION to u32,
        _TX_VERSION to u32,
        _CHECK_METADATA_HASH to u8
    ),
    defaultValues = mapOf(
        _CHECK_METADATA_HASH to METADATA_HASH_DISABLED
    )
) {
    const val BLOCK_HASH = _MORTALITY
    const val GENESIS = _GENESIS
    const val SPEC_VERSION = _SPEC_VERSION
    const val TX_VERSION = _TX_VERSION
    const val CHECK_METADATA_HASH = _CHECK_METADATA_HASH
}

open class ExtrinsicPayloadExtras(
    name: String,
    private val extrasMapping: Map<String, Type<*>>,
    // Used when a caller-built map omits an extension the runtime lists, so manual maps stay valid on newer runtimes.
    private val defaultValues: Map<String, Any?> = emptyMap()
) : Type<ExtrinsicPayloadExtrasInstance>(name) {

    override fun decode(
        scaleCodecReader: ScaleCodecReader,
        runtime: RuntimeSnapshot
    ): ExtrinsicPayloadExtrasInstance {
        val enabledSignedExtras = runtime.metadata.extrinsic.signedExtensions

        return enabledSignedExtras.associateWith { name ->
            extrasMapping[name]?.decode(scaleCodecReader, runtime)
        }
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: ExtrinsicPayloadExtrasInstance
    ) {
        val enabledSignedExtras = runtime.metadata.extrinsic.signedExtensions

        return enabledSignedExtras.forEach { name ->
            extrasMapping[name]?.encodeUnsafe(scaleCodecWriter, runtime, value[name] ?: defaultValues[name])
        }
    }

    override val isFullyResolved: Boolean = true

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is Map<*, *> && instance.keys.all { it is String }
    }

    private fun unknownSignedExtension(name: String): Nothing =
        throw EncodeDecodeException("Unknown signed extension: $name")
}
