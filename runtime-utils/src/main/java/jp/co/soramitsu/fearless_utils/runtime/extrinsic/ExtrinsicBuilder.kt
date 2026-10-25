package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.AdditionalExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.ExtrinsicPayloadExtrasInstance
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.new
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.SignatureInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import java.math.BigInteger

private val DEFAULT_TIP = BigInteger.ZERO
private val DEFAULT_APP_ID = BigInteger.ZERO
private val METADATA_HASH_DISABLED = BigInteger.ZERO

private const val PAYLOAD_HASH_THRESHOLD = 256

class ExtrinsicBuilder(
    val runtime: RuntimeSnapshot,
    private val keypair: Keypair,
    private val nonce: BigInteger,
    private val runtimeVersion: RuntimeVersion,
    private val genesisHash: ByteArray,
    private val multiChainEncryption: MultiChainEncryption,
    private val accountIdentifier: Any,
    private val blockHash: ByteArray = genesisHash,
    private val era: Era = Era.Immortal,
    private val tip: BigInteger = DEFAULT_TIP,
    private val appId: BigInteger? = null,
    private val signatureConstructor: Type.InstanceConstructor<SignatureWrapper> = SignatureInstanceConstructor
) {

    private val calls = mutableListOf<GenericCall.Instance>()

    /**
     * @throws CallMovedToAssetHubException if the runtime filters this call at dispatch since its pallet moved to Asset Hub
     */
    fun call(
        moduleIndex: Int,
        callIndex: Int,
        args: Map<String, Any?>
    ): ExtrinsicBuilder {
        val module = runtime.metadata.module(moduleIndex)
        val function = module.call(callIndex)

        return addCall(GenericCall.Instance(module, function, args))
    }

    /**
     * @throws CallMovedToAssetHubException if the runtime filters this call at dispatch since its pallet moved to Asset Hub
     */
    fun call(
        moduleName: String,
        callName: String,
        arguments: Map<String, Any?>
    ): ExtrinsicBuilder {
        val module = runtime.metadata.module(moduleName)
        val function = module.call(callName)

        return addCall(GenericCall.Instance(module, function, arguments))
    }

    fun build(
        useBatchAll: Boolean = false
    ): String {
        val call = maybeWrapInBatch(useBatchAll)
        val multiSignature = buildSignature(call)
        val signedExtras = buildSignedExtras()

        val extrinsic = Extrinsic.Instance(
            signature = Extrinsic.Signature.new(
                accountIdentifier = accountIdentifier,
                signature = multiSignature,
                signedExtras = signedExtras
            ),
            call = call
        )

        return Extrinsic.toHex(runtime, extrinsic)
    }

    /** Freezes exact SCALE bytes without touching the constructor's key or invoking a signer. */
    fun prepareSigning(useBatchAll: Boolean = false): PreparedExtrinsic {
        val callBytes = GenericCall.toByteArray(runtime, maybeWrapInBatch(useBatchAll))
        val extras = SignedExtras.bytes(runtime, buildSignedExtras())
        val additional = AdditionalExtras.bytes(runtime, buildAdditionalExtras())
        val addressType = requireNotNull(runtime.typeRegistry["Address"])
        val signatureType = requireNotNull(runtime.typeRegistry["ExtrinsicSignature"])
        return PreparedExtrinsic(
            runtime, multiChainEncryption, signatureConstructor, signatureType,
            (runtime.metadata.extrinsic.version.toInt() or 0x80).toByte(),
            addressType.bytes(runtime, accountIdentifier), extras, callBytes, callBytes + extras + additional
        )
    }

    // Refuse before signing: CheckMetadataHash makes relay-chain extrinsics valid, so a filtered call would be
    // included and charged for without doing anything.
    private fun addCall(call: GenericCall.Instance): ExtrinsicBuilder {
        runtime.metadata.requireNotMovedToAssetHub(call)

        calls.add(call)

        return this
    }

    private fun maybeWrapInBatch(useBatchAll: Boolean): GenericCall.Instance {
        return if (calls.size == 1) {
            calls.first()
        } else {
            wrapInBatch(useBatchAll)
        }
    }

    private fun buildSignature(call: GenericCall.Instance): Any? {
        val signedExtrasInstance = buildSignedExtras()

        val additionalExtrasInstance = buildAdditionalExtras()

        val payloadBytes = useScaleWriter {
            GenericCall.encode(this, runtime, call)
            SignedExtras.encode(this, runtime, signedExtrasInstance)
            AdditionalExtras.encode(this, runtime, additionalExtrasInstance)
        }

        val messageToSign = if (payloadBytes.size > PAYLOAD_HASH_THRESHOLD) {
            payloadBytes.blake2b256()
        } else {
            payloadBytes
        }

        val signatureWrapper = Signer.sign(multiChainEncryption, messageToSign, keypair)

        return signatureConstructor.constructInstance(runtime.typeRegistry, signatureWrapper)
    }

    private fun wrapInBatch(useBatchAll: Boolean): GenericCall.Instance {
        val batchModule = runtime.metadata.module("Utility")
        val batchFunctionName = if (useBatchAll) "batch_all" else "batch"
        val batchFunction = batchModule.call(batchFunctionName)

        return GenericCall.Instance(
            module = batchModule,
            function = batchFunction,
            arguments = mapOf(
                "calls" to calls
            )
        )
    }

    private fun buildSignedExtras(): ExtrinsicPayloadExtrasInstance = mapOf(
        SignedExtras.ERA to era,
        SignedExtras.TIP to tip,
        SignedExtras.ASSET_TX_PAYMENT to assetTxPayment(),
        SignedExtras.NONCE to nonce,
        SignedExtras.APP_ID to (appId ?: DEFAULT_APP_ID),
        SignedExtras.CHECK_METADATA_HASH to METADATA_HASH_DISABLED
    )

    private fun buildAdditionalExtras(): ExtrinsicPayloadExtrasInstance = mapOf(
        AdditionalExtras.BLOCK_HASH to blockHash,
        AdditionalExtras.GENESIS to genesisHash,
        AdditionalExtras.SPEC_VERSION to runtimeVersion.specVersion.toBigInteger(),
        AdditionalExtras.TX_VERSION to runtimeVersion.transactionVersion.toBigInteger(),
        AdditionalExtras.CHECK_METADATA_HASH to METADATA_HASH_DISABLED
    )

    // The tip travels in ChargeAssetTxPayment on asset-hub runtimes; the fee asset is always the native one (None).
    // The app id belongs to CheckAppId, never to the asset slot.
    private fun assetTxPayment(): List<Any?> = listOf(tip, null)
}
