package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlanner
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendRequest
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendService
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncSendBalanceProvider
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaSendRequest
import jp.co.soramitsu.common.data.network.solana.SolanaSendService
import jp.co.soramitsu.common.data.network.solana.SolanaTokenProgram
import jp.co.soramitsu.common.data.network.solana.SolanaTokenSendRequest
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.toLongExact
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.ext.normalizedBitcoinAddress
import jp.co.soramitsu.runtime.ext.normalizedIrohaAddress
import jp.co.soramitsu.runtime.ext.normalizedSolanaAddress
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.ext.universalWalletIrohaNetwork
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.BigInteger

class TransferServiceProvider(
    private val substrateSource: SubstrateRemoteSource,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val keyPairRepository: KeypairProvider,
    private val accountRepository: AccountRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val assetDao: AssetDao,
    private val bitcoinIndexerClient: BitcoinIndexerClient,
    private val solanaRpcClient: SolanaRpcClient,
    private val solanaBalanceSync: SolanaBalanceSource,
    private val irohaToriiClient: IrohaToriiClient,
    private val irohaTransferSigner: IrohaTransferSigner = UnavailableIrohaTransferSigner,
    private val sendPausePolicy: SendPausePolicy = SendPausePolicy.Unrestricted,
    private val quoteStore: NativeSendQuoteStore = NativeSendQuoteStore(),
    private val sendJournal: jp.co.soramitsu.common.data.network.send.WalletSendJournal? = null,
    private val currentChain: (suspend (String) -> Chain)? = null
) {
    fun provide(chain: Chain): TransferService {
        if (sendPausePolicy.isPaused(chain)) {
            return PausedTransferService { sendPausePolicy.pausedError(chain) }
        }
        return if (chain.isUniversalWalletBitcoin()) {
            BitcoinTransferService(
                chain = chain,
                accountRepository = accountRepository,
                client = bitcoinIndexerClient,
                quotes = quoteStore,
                executor = NativeSendExecutor(requireNotNull(sendJournal)),
                pausePolicy = sendPausePolicy
            )
        } else if (chain.isUniversalWalletSolana()) {
            SolanaTransferService(
                chain = chain,
                accountRepository = accountRepository,
                rpcClient = solanaRpcClient,
                quotes = quoteStore,
                executor = NativeSendExecutor(requireNotNull(sendJournal)),
                pausePolicy = sendPausePolicy,
                currentChain = { currentChain?.invoke(chain.id) ?: chain }
            )
        } else if (chain.isUniversalWalletIroha()) {
            IrohaTransferService(
                chain = chain,
                accountRepository = accountRepository,
                toriiClient = irohaToriiClient,
                signer = irohaTransferSigner
            )
        } else when (chain.ecosystem) {
            Ecosystem.EthereumBased, Ecosystem.Substrate -> SubstrateTransferService(
                chain,
                substrateSource
            )
            Ecosystem.Ethereum -> EthereumTransferService(
                chain,
                ethereumRemoteSource,
                keyPairRepository
            )
            Ecosystem.Ton -> TonTransferService(chain, keyPairRepository, accountRepository, tonRemoteSource, assetDao)
            else -> throw IllegalStateException("Unsupported chain for transfers: ${chain.name}, ecosystem: ${chain.ecosystem.name}")
        }
    }
}

interface TransferService {
    suspend fun getTransferFee(transfer: Transfer): BigDecimal
    /** Protocol-specific exact maximum; unsupported protocols retain their existing quick inputs. */
    suspend fun getTransferMaxAmount(transfer: Transfer): BigDecimal = throw UnsupportedOperationException()
    fun observeTransferFee(transfer: Transfer): Flow<BigDecimal>
    suspend fun transfer(transfer: Transfer): String
}

interface IrohaTransferSigner {
    suspend fun buildAndSignTransfer(request: IrohaTransferSigningRequest): IrohaSignedTransfer
}

object UnavailableIrohaTransferSigner : IrohaTransferSigner {
    override suspend fun buildAndSignTransfer(request: IrohaTransferSigningRequest): IrohaSignedTransfer {
        throw IllegalStateException("Iroha transfer signing codec is unavailable")
    }
}

data class IrohaTransferSigningRequest(
    val amount: String,
    val assetDefinitionId: String,
    val authority: String,
    val chainId: String,
    val derivationPath: String,
    val destinationAccountId: String,
    val mnemonicOrSeed: String,
    val network: String,
    val signingPublicKeyHex: String,
    val sourceAccountId: String,
    val sourceAssetId: String,
    val transactionMetadata: IrohaTransferMetadata = IrohaTransferMetadata.empty()
)

class IrohaSignedTransfer(
    val signedTransaction: ByteArray,
    val transactionHashHex: String? = null
)

class IrohaTransferService(
    private val chain: Chain,
    private val accountRepository: AccountRepository,
    private val toriiClient: IrohaToriiClient,
    private val signer: IrohaTransferSigner
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        resolveContext(transfer)

        return BigDecimal.ZERO
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow { emit(getTransferFee(transfer)) }
    }

    override suspend fun transfer(transfer: Transfer): String {
        val context = resolveContext(transfer)
        return signAndSubmit(context)
    }

    /**
     * Operator-only funded-smoke seam. Production DI remains fail closed because it supplies
     * [UnavailableIrohaTransferSigner]; this method does not alter any release enablement flag.
     */
    suspend fun transferWalletSmokeEvidence(
        transfer: Transfer,
        untrustedMetadata: Map<*, *>
    ): String {
        // Snapshot and validate operator input before account, key, signer, or Torii work.
        val validatedMetadata = IrohaTransferMetadata.walletSmoke(untrustedMetadata)
        val evidenceNetwork = chain.universalWalletIrohaNetwork()
        if (evidenceNetwork != UniversalWalletRegistry.nexus) {
            throw unsupported("Iroha wallet-smoke evidence requires SORA Nexus")
        }
        val evidenceToriiBaseUrl = chain.externalApi?.history
            ?.takeIf { it.type == Chain.ExternalApi.Section.Type.IROHA }
            ?.url
            ?.takeIf(String::isNotBlank)
            ?: evidenceNetwork.toriiBaseUrl
            ?: throw unsupported("Canonical SORA Nexus Minamoto endpoint is unavailable")
        val canonicalMinamoto = UniversalWalletRegistry.nexus.toriiBaseUrl
            ?: throw unsupported("Canonical SORA Nexus Minamoto endpoint is unavailable")
        if (evidenceToriiBaseUrl != canonicalMinamoto) {
            throw unsupported("Iroha wallet-smoke evidence requires canonical SORA Nexus Minamoto")
        }

        val context = resolveContext(transfer)
        if (
            context.signingRequest.network != IrohaTransferMetadata.NEXUS_NETWORK ||
            context.signingRequest.chainId != IrohaTransferMetadata.NEXUS_CHAIN_ID
        ) {
            throw unsupported("Iroha wallet-smoke evidence requires SORA Nexus")
        }
        if (context.toriiBaseUrl != canonicalMinamoto) {
            throw unsupported("Iroha wallet-smoke evidence requires canonical SORA Nexus Minamoto")
        }

        val evidenceContext = context.copy(
            signingRequest = context.signingRequest.withValidatedWalletSmokeMetadata(
                validatedMetadata
            )
        )
        return signAndSubmit(evidenceContext)
    }

    private suspend fun signAndSubmit(context: IrohaTransferContext): String {
        val signedTransfer = signer.buildAndSignTransfer(context.signingRequest)
        if (signedTransfer.signedTransaction.isEmpty()) {
            throw unsupported("Iroha transfer signer returned an empty transaction for ${chain.name}")
        }

        val receipt = toriiClient.submitTransaction(
            noritoBytes = signedTransfer.signedTransaction,
            baseUrl = context.toriiBaseUrl
        )

        return signedTransfer.transactionHashHex
            ?: receipt.payload.signedTransactionHash
            ?: receipt.payload.txHash
    }

    private suspend fun resolveContext(transfer: Transfer): IrohaTransferContext {
        val network = chain.universalWalletIrohaNetwork()
            ?: throw unsupported("Iroha network is not configured for ${chain.name}")
        val toriiBaseUrl = chain.externalApi?.history
            ?.takeIf { it.type == Chain.ExternalApi.Section.Type.IROHA }
            ?.url
            ?.takeIf(String::isNotBlank)
            ?: network.toriiBaseUrl
            ?: throw unsupported("Iroha Torii endpoint is not configured for ${chain.name}")
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val sourceAddress = metaAccount.address(chain)?.let(chain::normalizedIrohaAddress)
            ?: throw unsupported("Selected wallet has no Iroha account for ${chain.name}")
        val sourceDetails = IrohaAddressCodec.parse(sourceAddress, network.chainDiscriminant)
        val senderAddress = chain.normalizedIrohaAddress(transfer.sender)
            ?: throw unsupported("Iroha sender address is invalid for ${chain.name}")

        if (senderAddress != sourceAddress) {
            throw unsupported("Iroha sender does not match selected wallet for ${chain.name}")
        }

        val destinationAddress = chain.normalizedIrohaAddress(transfer.recipient)
            ?: throw unsupported("Iroha recipient address is invalid for ${chain.name}")
        val amount = transfer.amount.toPlainString().normalizeIrohaTransferAmount()
        val assetDefinitionId = transfer.chainAsset.id.normalizeIrohaAssetDefinitionId()
        val mnemonic = resolveRootMnemonic(metaAccount.id)
        val derivedAddress = runCatching {
            IrohaKeyDerivation.deriveAddress(
                mnemonic = mnemonic,
                chainDiscriminant = network.chainDiscriminant
            ).i105
        }.getOrElse {
            throw unsupported("Iroha mnemonic root material is invalid for ${chain.name}")
        }

        if (derivedAddress != sourceAddress) {
            throw unsupported("Iroha mnemonic does not match selected wallet for ${chain.name}")
        }

        return IrohaTransferContext(
            toriiBaseUrl = toriiBaseUrl,
            signingRequest = IrohaTransferSigningRequest(
                amount = amount,
                assetDefinitionId = assetDefinitionId,
                authority = sourceAddress,
                chainId = network.chainId,
                derivationPath = UniversalWalletDerivationPaths.IROHA_DEFAULT,
                destinationAccountId = destinationAddress,
                mnemonicOrSeed = mnemonic,
                network = network.irohaTransferNetworkKey(),
                signingPublicKeyHex = sourceDetails.publicKeyHex,
                sourceAccountId = sourceAddress,
                sourceAssetId = "$assetDefinitionId#$sourceAddress"
            )
        )
    }

    private suspend fun resolveRootMnemonic(metaId: Long): String {
        accountRepository.getSubstrateSecrets(metaId)
            ?.get(SubstrateSecrets.Entropy)
            ?.let { return MnemonicCreator.fromEntropy(it.clone()).words }

        accountRepository.getEthereumSecrets(metaId)
            ?.get(EthereumSecrets.Entropy)
            ?.let { return MnemonicCreator.fromEntropy(it.clone()).words }

        accountRepository.getTonSecrets(metaId)
            ?.get(TonSecrets.Seed)
            ?.decodeToString()
            ?.normalizeMnemonic()
            ?.let { return it }

        throw unsupported("Iroha transfers require mnemonic root material for ${chain.name}")
    }

    private fun String.normalizeMnemonic(): String? {
        val words = trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun String.normalizeIrohaTransferAmount(): String {
        if (!IROHA_AMOUNT_PATTERN.matches(this)) {
            throw unsupported("Iroha transfer amount is invalid for ${chain.name}")
        }

        val parts = split('.')
        val whole = parts[0].toBigInteger()
        val fraction = parts.getOrNull(1).orEmpty()
        if (whole == BigInteger.ZERO && fraction.all { it == '0' }) {
            throw unsupported("Iroha transfer amount must be greater than zero for ${chain.name}")
        }

        return this
    }

    private fun String.normalizeIrohaAssetDefinitionId(): String {
        if (!IROHA_ASSET_DEFINITION_ID_PATTERN.matches(this)) {
            throw unsupported("Iroha asset definition id is invalid for ${chain.name}")
        }

        return this
    }

    private fun UniversalWalletRegistry.IrohaNetwork.irohaTransferNetworkKey(): String {
        return when (this) {
            UniversalWalletRegistry.taira -> "taira"
            UniversalWalletRegistry.nexus -> "nexus"
            else -> id
        }
    }

    private fun unsupported(message: String): IllegalStateException {
        return IllegalStateException(message)
    }

    private data class IrohaTransferContext(
        val toriiBaseUrl: String,
        val signingRequest: IrohaTransferSigningRequest
    )

    private companion object {
        val IROHA_AMOUNT_PATTERN = Regex("(?:0|[1-9]\\d*)(?:\\.\\d{1,28})?")
        val IROHA_ASSET_DEFINITION_ID_PATTERN = Regex("[^\\s%/?:#]+#[^\\s%/?:#]+")
    }
}

class SubstrateTransferService(
    private val chain: Chain,
    private val substrateSource: SubstrateRemoteSource
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        val transferFee = substrateSource.getTransferFee(chain, transfer, null, false)
        return chain.utilityAsset?.amountFromPlanks(transferFee)
            ?: throw IllegalStateException("cannot calculate substrate fee ${chain.name}, fee: $transferFee")
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow { emit(getTransferFee(transfer)) }
    }

    override suspend fun transfer(transfer: Transfer): String {
        require(transfer.additionalParams is SubstrateTransferParams) { "Wrong additional params type for substrate transfer" }

        val accountId = transfer.sender.toAccountId()
        return substrateSource.performTransfer(
            accountId,
            chain,
            transfer,
            null,
            false
        )
    }
}

class EthereumTransferService(
    private val chain: Chain,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val keyPairRepository: KeypairProvider,
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        return observeTransferFee(transfer).first()
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return ethereumRemoteSource.listenGas(transfer, chain).map {
            chain.utilityAsset?.amountFromPlanks(it)
                ?: throw IllegalStateException("cannot calculate ethereum fee ${chain.name}, fee: $it")
        }
    }

    override suspend fun transfer(transfer: Transfer): String {
        val accountId = transfer.sender.fromHex()
        val keypair = keyPairRepository.getKeypairFor(chain, accountId)
        val privateKey = keypair.privateKey

        return ethereumRemoteSource.performTransfer(chain, transfer, privateKey.toHexString(true))
            .requireValue() // handle error
    }
}
