package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.normalizeAuthorStatusBlockHash
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger

private val DEFAULT_TIP = BigInteger.ZERO
private val DEFAULT_FEE_ACCOUNT_ID = ByteArray(32)
private val DEFAULT_FEE_KEYPAIR = BaseKeypair(ByteArray(32) { 1 }, ByteArray(32))
private const val WATCH_TIMEOUT_MILLIS = 120_000L

class ExtrinsicBuilderFactory(
    private val rpcCalls: RpcCalls,
    private val chainRegistry: IChainRegistry,
    private val mortalityConstructor: MortalityConstructor
) {
    suspend fun createForSubmit(
        chain: IChain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        tip: BigInteger?,
        appId: BigInteger?
    ): ExtrinsicBuilder {
        val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
        val keypair = keypairProvider.getKeypairFor(chain, accountId)

        return create(
            chain = chain,
            accountId = accountId,
            keypair = keypair,
            encryption = mapCryptoTypeToEncryption(cryptoType),
            nonce = rpcCalls.getAccountNonce(chain, accountId),
            mortality = mortalityConstructor.construct(chain),
            tip = tip,
            appId = appId
        )
    }

    suspend fun createForFee(chain: IChain): ExtrinsicBuilder {
        return create(
            chain = chain,
            accountId = DEFAULT_FEE_ACCOUNT_ID,
            keypair = DEFAULT_FEE_KEYPAIR,
            encryption = EncryptionType.ED25519,
            nonce = BigInteger.ZERO,
            mortality = mortalityConstructor.construct(chain),
            tip = null,
            appId = null
        )
    }

    suspend fun createForFee(
        chain: IChain,
        accountId: ByteArray,
        keypairProvider: KeypairProvider,
        tip: BigInteger?,
        appId: BigInteger?
    ): ExtrinsicBuilder {
        val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
        val keypair = keypairProvider.getKeypairFor(chain, accountId)

        return create(
            chain = chain,
            accountId = accountId,
            keypair = keypair,
            encryption = mapCryptoTypeToEncryption(cryptoType),
            nonce = rpcCalls.getAccountNonce(chain, accountId),
            mortality = mortalityConstructor.construct(chain),
            tip = tip,
            appId = appId
        )
    }

    private suspend fun create(
        chain: IChain,
        accountId: ByteArray,
        keypair: Keypair,
        encryption: EncryptionType,
        nonce: BigInteger,
        mortality: Mortality,
        tip: BigInteger?,
        appId: BigInteger?
    ): ExtrinsicBuilder {
        val runtime = chainRegistry.getRuntime(chain.id)

        return ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = nonce,
            runtimeVersion = rpcCalls.getRuntimeVersion(chain.id),
            genesisHash = chain.id.fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(encryption),
            accountIdentifier = AddressInstanceConstructor.constructInstance(runtime.typeRegistry, accountId),
            blockHash = mortality.blockHash,
            era = mortality.era,
            tip = tip ?: DEFAULT_TIP,
            appId = appId
        )
    }
}

class ExtrinsicService(
    private val rpcCalls: RpcCalls,
    private val keypairProvider: KeypairProvider,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {
    fun createSignature(
        encryption: EncryptionType,
        keypair: Keypair,
        message: String
    ): String {
        val signatureWrapper = Signer.sign(
            MultiChainEncryption.Substrate(encryption),
            message.fromHex(),
            keypair
        )

        val bytes = byteArrayOf(1) + signatureWrapper.signature

        return bytes.toHexString(withPrefix = true)
    }

    suspend fun submitExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val extrinsic = buildSubmitExtrinsic(chain, accountId, useBatchAll, tip, appId, formExtrinsic)
        rpcCalls.submitExtrinsic(chain.id, extrinsic)
    }

    suspend fun estimateFee(
        chain: IChain,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val extrinsic = buildFeeExtrinsic(chain, useBatchAll, formExtrinsic)
        return rpcCalls.estimateExtrinsicFee(chain.id, extrinsic)
    }

    suspend fun estimateFee(
        chain: IChain,
        accountId: ByteArray,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val extrinsic = buildFeeExtrinsic(chain, accountId, useBatchAll, tip, appId, formExtrinsic)
        return rpcCalls.estimateExtrinsicFee(chain.id, extrinsic)
    }

    suspend fun submitAndWatchExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Pair<String, String>? = runCatching {
        val extrinsic = buildSubmitExtrinsic(chain, accountId, useBatchAll, tip, appId, formExtrinsic)
        val txHash = extrinsic.fromHex().blake2b256().toHexString(withPrefix = true)
        val blockHash = withTimeoutOrNull(WATCH_TIMEOUT_MILLIS) {
            rpcCalls.extrinsicStatusFlow(chain.id, extrinsic)
                .mapNotNull { normalizeAuthorStatusBlockHash(it.params.result) }
                .first()
        }

        blockHash?.let { txHash to it }
    }.getOrNull()

    private suspend fun buildSubmitExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        useBatchAll: Boolean,
        tip: BigInteger?,
        appId: BigInteger?,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): String {
        val builder = extrinsicBuilderFactory.createForSubmit(
            chain = chain,
            accountId = accountId,
            keypairProvider = keypairProvider,
            tip = tip,
            appId = appId
        )
        builder.formExtrinsic()

        return builder.build(useBatchAll)
    }

    private suspend fun buildFeeExtrinsic(
        chain: IChain,
        useBatchAll: Boolean,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): String {
        val builder = extrinsicBuilderFactory.createForFee(chain)
        builder.formExtrinsic()

        return builder.build(useBatchAll)
    }

    private suspend fun buildFeeExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        useBatchAll: Boolean,
        tip: BigInteger?,
        appId: BigInteger?,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): String {
        val builder = extrinsicBuilderFactory.createForFee(
            chain = chain,
            accountId = accountId,
            keypairProvider = keypairProvider,
            tip = tip,
            appId = appId
        )
        builder.formExtrinsic()

        return builder.build(useBatchAll)
    }
}
