package jp.co.soramitsu.core.extrinsic

import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.normalizeAuthorStatusBlockHash
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

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

    /**
     * Resolve public runtime data without reading a key or signing a fee placeholder. A mutation is always mortal:
     * when no mortal era can be built this fails instead of signing an immortal transaction, which a node that
     * accepted it could include at any later time.
     */
    suspend fun createForAuthorizedSubmit(
        chain: IChain, accountId: ByteArray, keypairProvider: KeypairProvider, tip: BigInteger?, appId: BigInteger?
    ): AuthorizedExtrinsicBuilder {
        val cryptoType = keypairProvider.getCryptoTypeFor(chain, accountId)
        val mortality = mortalityConstructor.construct(chain)
        check(mortality.era is Era.Mortal) { "A mortal transaction era is unavailable" }
        val builder = create(
            chain, accountId, DEFAULT_FEE_KEYPAIR, mapCryptoTypeToEncryption(cryptoType),
            rpcCalls.getAccountNonce(chain, accountId), mortality, tip, appId
        )
        return AuthorizedExtrinsicBuilder(builder, mortality.validity)
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

        return create(
            chain = chain,
            accountId = accountId,
            keypair = DEFAULT_FEE_KEYPAIR,
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

/** A builder for a mortal mutation and the blocks its transaction can be included in, when they are known. */
class AuthorizedExtrinsicBuilder(val builder: ExtrinsicBuilder, val validity: ExtrinsicValidity?)

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

    /** Does not decrypt key material; used only to present local Substrate action availability. */
    suspend fun hasLocalSubstrateKey(chain: IChain, accountId: ByteArray): Boolean =
        (keypairProvider as? GuardedKeypairProvider)?.hasLocalSubstrateKey(chain, accountId) == true

    /** Keep reviewed wallet identity through public metadata, guarded key lookup and submission. */
    suspend fun submitAuthorizedExtrinsicForWallet(
        chain: IChain,
        accountId: ByteArray,
        walletId: Long,
        authorize: (String) -> MutationExecutionGuard,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val provider = (keypairProvider as? WalletBoundKeypairProvider)
            ?: error("This signing provider does not support wallet-bound mutations")
        ExtrinsicService(rpcCalls, provider.forWallet(walletId), extrinsicBuilderFactory)
            .submitAuthorizedExtrinsic(chain, accountId, authorize, useBatchAll, tip, appId, formExtrinsic)
            .getOrThrow()
    }

    /** Wallet-bound submission with durable bytes and locally verified node acknowledgement. */
    suspend fun submitTrackedExtrinsicForWallet(
        chain: IChain,
        accountId: ByteArray,
        walletId: Long,
        authorize: (String) -> MutationExecutionGuard,
        tracking: ExtrinsicSubmissionTracking,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val provider = (keypairProvider as? WalletBoundKeypairProvider)
            ?: error("This signing provider does not support wallet-bound mutations")
        ExtrinsicService(rpcCalls, provider.forWallet(walletId), extrinsicBuilderFactory)
            .executeAuthorizedExtrinsic(chain, accountId, authorize, useBatchAll, null, null, tracking, formExtrinsic)
    }

    /** New feature path: freeze intent, acquire lease, then authorize key read, sign and true send. */
    suspend fun submitAuthorizedExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        authorize: (String) -> MutationExecutionGuard,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        executeAuthorizedExtrinsic(chain, accountId, authorize, useBatchAll, tip, appId, null, formExtrinsic)
    }

    private suspend fun executeAuthorizedExtrinsic(
        chain: IChain,
        accountId: ByteArray,
        authorize: (String) -> MutationExecutionGuard,
        useBatchAll: Boolean,
        tip: BigInteger?,
        appId: BigInteger?,
        tracking: ExtrinsicSubmissionTracking?,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): String {
        val account = accountId.copyOf()
        val chainId = chain.id
        val provider = keypairProvider as? GuardedKeypairProvider
            ?: error("This signing provider does not support authorized mutations")
        val authorized = extrinsicBuilderFactory.createForAuthorizedSubmit(chain, account, provider, tip, appId)
        val builder = authorized.builder
        builder.formExtrinsic()
        val prepared = builder.prepareSigning(useBatchAll)
        val intent = mutationIntentSha256(chainId, account, prepared.intentBytes())
        val lease = authorize(intent)
        lease.check(intent)
        val key = provider.getAuthorizedKeypairFor(chain, account, lease, intent)
        lease.check(intent)
        val signed = prepared.sign(key) { encryption, message, signingKey ->
            // Freeze/hash payload and initialize providers before the final sample.
            val primitive = Signer.prepareSigning(encryption, message, signingKey)
            primitive.sign { sign -> lease.runIfAuthorized(intent, sign) }
        }
        if (tracking == null) return rpcCalls.submitAuthorizedExtrinsic(chainId, signed, lease, intent)
        try {
            authorized.validity?.let(tracking::validity)
            val retainedGuard = tracking.guard(signed, intent, lease)
            val expectedHash = signed.fromHex().blake2b256().toHexString(withPrefix = true)
            val response = rpcCalls.submitAuthorizedExtrinsic(chainId, signed, retainedGuard, intent)
            require(response == expectedHash) { "Node acknowledgement does not match the retained transaction" }
            tracking.acknowledged(expectedHash)
            return expectedHash
        } finally {
            tracking.finished()
        }
    }

    /** Public-only fee preparation uses the same reviewed wallet namespace as submission. */
    suspend fun estimateFeeForWallet(
        chain: IChain,
        accountId: ByteArray,
        walletId: Long,
        useBatchAll: Boolean = false,
        tip: BigInteger? = null,
        appId: BigInteger? = null,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        val provider = (keypairProvider as? WalletBoundKeypairProvider)
            ?: error("This provider does not support wallet-bound fee metadata")
        return ExtrinsicService(rpcCalls, provider.forWallet(walletId), extrinsicBuilderFactory)
            .estimateFee(chain, accountId, useBatchAll, tip, appId, formExtrinsic)
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

        return builder.buildFeePlaceholder(useBatchAll)
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

        return builder.buildFeePlaceholder(useBatchAll)
    }

    /** Fee RPCs need the signed envelope's shape, not an authorized signature or private key. */
    private fun ExtrinsicBuilder.buildFeePlaceholder(useBatchAll: Boolean): String =
        prepareSigning(useBatchAll).sign(DEFAULT_FEE_KEYPAIR) { encryption, _, _ ->
            val type = when (encryption) {
                is MultiChainEncryption.Substrate -> encryption.encryptionType
                MultiChainEncryption.Ethereum -> EncryptionType.ECDSA
            }
            when (type) {
                EncryptionType.ED25519 -> SignatureWrapper.Ed25519(ByteArray(64))
                EncryptionType.SR25519 -> SignatureWrapper.Sr25519(ByteArray(64))
                EncryptionType.ECDSA -> SignatureWrapper.Ecdsa(ByteArray(1), ByteArray(32), ByteArray(32))
            }
        }
}

/** Length-framed context binds the frozen SCALE bytes to the exact network and sender. */
internal fun mutationIntentSha256(chainId: String, accountId: ByteArray, prepared: ByteArray): String {
    val bytes = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeUTF("FearlessWallet-MutationIntent-v1")
            output.writeUTF(chainId)
            output.writeInt(accountId.size)
            output.write(accountId)
            output.writeInt(prepared.size)
            output.write(prepared)
        }
    }.toByteArray()
    return MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(withPrefix = false)
}
