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
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
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
    private val solanaBalanceSync: SolanaBalanceSync,
    private val irohaToriiClient: IrohaToriiClient,
    private val irohaTransferSigner: IrohaTransferSigner = UnavailableIrohaTransferSigner
) {
    fun provide(chain: Chain): TransferService {
        return if (chain.isUniversalWalletBitcoin()) {
            BitcoinTransferService(
                chain = chain,
                accountRepository = accountRepository,
                sendPlanner = BitcoinSendPlanner(bitcoinIndexerClient),
                sendService = BitcoinSendService(bitcoinIndexerClient)
            )
        } else if (chain.isUniversalWalletSolana()) {
            SolanaTransferService(
                chain = chain,
                accountRepository = accountRepository,
                rpcClient = solanaRpcClient,
                balanceSync = solanaBalanceSync
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
    fun observeTransferFee(transfer: Transfer): Flow<BigDecimal>
    suspend fun transfer(transfer: Transfer): String
}

class BitcoinTransferService(
    private val chain: Chain,
    private val accountRepository: AccountRepository,
    private val sendPlanner: BitcoinSendPlanner,
    private val sendService: BitcoinSendService
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        val context = resolveContext(transfer)
        val plan = sendPlanner.plan(
            amountSats = transfer.amountSats(),
            sources = listOf(BitcoinUtxoSource(context.sourceAddress)),
            recipientAddress = transfer.recipient,
            changeAddress = context.sourceAddress,
            network = context.network,
            baseUrl = context.baseUrl
        )

        return transfer.chainAsset.amountFromPlanks(BigInteger.valueOf(plan.feeSats))
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow { emit(getTransferFee(transfer)) }
    }

    override suspend fun transfer(transfer: Transfer): String {
        val context = resolveContext(transfer)
        val mnemonic = resolveRootMnemonic(context.metaAccount.id)
        requireMnemonicMatchesSelectedWallet(mnemonic, context)
        val result = sendService.send(
            BitcoinSendRequest(
                mnemonic = mnemonic,
                amountSats = transfer.amountSats(),
                sources = listOf(BitcoinUtxoSource(context.sourceAddress)),
                recipientAddress = transfer.recipient,
                changeAddress = context.sourceAddress,
                network = context.network,
                baseUrl = context.baseUrl
            )
        )

        return result.broadcastTxid
    }

    private suspend fun resolveContext(transfer: Transfer): BitcoinTransferContext {
        val network = chain.universalWalletBitcoinIndexerNetwork()
            ?: throw unsupported("Bitcoin network is not configured for ${chain.name}")
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val sourceAddress = metaAccount.address(chain)?.let(chain::normalizedBitcoinAddress)
            ?: throw unsupported("Selected wallet has no Bitcoin account for ${chain.name}")
        val senderAddress = chain.normalizedBitcoinAddress(transfer.sender)
            ?: throw unsupported("Bitcoin sender address is invalid for ${chain.name}")

        if (senderAddress != sourceAddress) {
            throw unsupported("Bitcoin sender does not match selected wallet for ${chain.name}")
        }

        return BitcoinTransferContext(
            metaAccount = metaAccount,
            sourceAddress = sourceAddress,
            network = network,
            baseUrl = chain.externalApi?.history
                ?.takeIf { it.type == Chain.ExternalApi.Section.Type.BITCOIN }
                ?.url
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

        throw unsupported("Bitcoin transfers require mnemonic root material for ${chain.name}")
    }

    private fun requireMnemonicMatchesSelectedWallet(
        mnemonic: String,
        context: BitcoinTransferContext
    ) {
        val derivedAddress = runCatching {
            BitcoinKeyDerivation.deriveAccount(
                mnemonic = mnemonic,
                network = context.network.bitcoinKeyDerivationNetwork()
            ).firstReceiveAddress
        }.getOrElse {
            throw unsupported("Bitcoin mnemonic root material is invalid for ${chain.name}")
        }

        if (!derivedAddress.equals(context.sourceAddress, ignoreCase = true)) {
            throw unsupported("Bitcoin mnemonic does not match selected wallet for ${chain.name}")
        }
    }

    private fun BitcoinIndexerRoutes.Network.bitcoinKeyDerivationNetwork(): BitcoinKeyDerivation.Network {
        return when (this) {
            BitcoinIndexerRoutes.Network.Mainnet -> BitcoinKeyDerivation.Network.Mainnet
            BitcoinIndexerRoutes.Network.Testnet -> BitcoinKeyDerivation.Network.Testnet
        }
    }

    private fun Transfer.amountSats(): Long {
        return try {
            amountInPlanks.toLongExact()
        } catch (_: ArithmeticException) {
            throw unsupported("Bitcoin transfer amount is outside the supported satoshi range")
        }
    }

    private fun String.normalizeMnemonic(): String? {
        val words = trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun unsupported(message: String): IllegalStateException {
        return IllegalStateException(message)
    }

    private data class BitcoinTransferContext(
        val metaAccount: MetaAccount,
        val sourceAddress: String,
        val network: BitcoinIndexerRoutes.Network,
        val baseUrl: String?
    )
}

class SolanaTransferService(
    private val chain: Chain,
    private val accountRepository: AccountRepository,
    private val rpcClient: SolanaRpcClient,
    private val balanceSync: SolanaBalanceSync
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        val context = resolveContext(transfer)
        return if (transfer.chainAsset.isSolanaNativeAsset(context.network)) {
            getNativeTransferFee(transfer, context)
        } else {
            getTokenTransferFee(transfer, context)
        }
    }

    private suspend fun getNativeTransferFee(
        transfer: Transfer,
        context: SolanaTransferContext
    ): BigDecimal {
        val lamports = transfer.amountLamports()
        SolanaTransferTransactionBuilder.requireLamports(lamports)
        val blockhash = rpcClient.latestBlockhash(rpcUrl = context.rpcUrl)
        val unsigned = SolanaTransferTransactionBuilder.buildNativeTransfer(
            senderAddress = context.sourceAddress,
            recipientAddress = transfer.recipient,
            lamports = lamports,
            recentBlockhash = blockhash.value.blockhash
        )
        val fee = rpcClient.feeForMessage(
            messageBase64 = unsigned.messageBase64,
            rpcUrl = context.rpcUrl
        ).value ?: throw unsupported("Solana transfer fee is unavailable for ${chain.name}")

        return solanaFeeAmountFromLamports(fee, context.network)
    }

    private suspend fun getTokenTransferFee(
        transfer: Transfer,
        context: SolanaTransferContext
    ): BigDecimal {
        val token = resolveTokenTransferContext(transfer, context)
        val destinationTokenAccount = SolanaTransferTransactionBuilder.deriveAssociatedTokenAccountAddress(
            walletAddress = transfer.recipient,
            mintAddress = token.mintAddress,
            tokenProgram = SolanaTokenProgram.SplToken
        )
        val destinationTokenAccountExists = rpcClient.accountExists(
            address = destinationTokenAccount,
            rpcUrl = context.rpcUrl
        )
        val blockhash = rpcClient.latestBlockhash(rpcUrl = context.rpcUrl)
        val unsigned = SolanaTransferTransactionBuilder.buildTokenSendChecked(
            ownerAddress = context.sourceAddress,
            sourceTokenAccount = token.sourceTokenAccount,
            destinationTokenAccount = destinationTokenAccount.takeIf { destinationTokenAccountExists },
            mintAddress = token.mintAddress,
            rawAmount = token.rawAmount,
            decimals = token.decimals,
            recentBlockhash = blockhash.value.blockhash,
            tokenProgram = SolanaTokenProgram.SplToken,
            destinationWalletAddress = transfer.recipient.takeUnless { destinationTokenAccountExists }
        )
        val fee = rpcClient.feeForMessage(
            messageBase64 = unsigned.messageBase64,
            rpcUrl = context.rpcUrl
        ).value ?: throw unsupported("Solana token transfer fee is unavailable for ${chain.name}")

        return solanaFeeAmountFromLamports(fee, context.network)
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow { emit(getTransferFee(transfer)) }
    }

    override suspend fun transfer(transfer: Transfer): String {
        val context = resolveContext(transfer)
        val sendService = SolanaSendService(
            rpcClient = rpcClient,
            balanceProvider = SolanaBalanceSyncSendBalanceProvider(
                balanceSync = balanceSync,
                network = context.network,
                baseUrl = context.indexerBaseUrl
            )
        )

        return if (transfer.chainAsset.isSolanaNativeAsset(context.network)) {
            transferNative(transfer, context, sendService)
        } else {
            transferToken(transfer, context, sendService)
        }
    }

    private suspend fun transferNative(
        transfer: Transfer,
        context: SolanaTransferContext,
        sendService: SolanaSendService
    ): String {
        val mnemonic = resolveRootMnemonic(context.metaAccount.id)
        requireMnemonicMatchesSelectedWallet(mnemonic, context)
        val result = sendService.send(
            SolanaSendRequest(
                mnemonic = mnemonic,
                recipientAddress = transfer.recipient,
                lamports = transfer.amountLamports(),
                rpcUrl = context.rpcUrl
            )
        )

        return result.signature
    }

    private suspend fun transferToken(
        transfer: Transfer,
        context: SolanaTransferContext,
        sendService: SolanaSendService
    ): String {
        val mnemonic = resolveRootMnemonic(context.metaAccount.id)
        requireMnemonicMatchesSelectedWallet(mnemonic, context)
        val token = resolveTokenTransferContext(transfer, context)
        val result = sendService.sendTokenTransfer(
            SolanaTokenSendRequest(
                mnemonic = mnemonic,
                sourceTokenAccount = token.sourceTokenAccount,
                mintAddress = token.mintAddress,
                rawAmount = token.rawAmount,
                decimals = token.decimals,
                tokenProgram = SolanaTokenProgram.SplToken,
                destinationWalletAddress = transfer.recipient,
                rpcUrl = context.rpcUrl
            )
        )

        return result.signature
    }

    private suspend fun resolveContext(transfer: Transfer): SolanaTransferContext {
        val network = chain.universalWalletSolanaIndexerNetwork()
            ?: throw unsupported("Solana network is not configured for ${chain.name}")
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val sourceAddress = metaAccount.address(chain)?.let(chain::normalizedSolanaAddress)
            ?: throw unsupported("Selected wallet has no Solana account for ${chain.name}")
        val senderAddress = chain.normalizedSolanaAddress(transfer.sender)
            ?: throw unsupported("Solana sender address is invalid for ${chain.name}")

        if (senderAddress != sourceAddress) {
            throw unsupported("Solana sender does not match selected wallet for ${chain.name}")
        }

        return SolanaTransferContext(
            metaAccount = metaAccount,
            sourceAddress = sourceAddress,
            network = network,
            indexerBaseUrl = chain.externalApi?.history
                ?.takeIf { it.type == Chain.ExternalApi.Section.Type.SOLANA }
                ?.url,
            rpcUrl = chain.nodes.firstOrNull { it.isActive }?.url
                ?: chain.nodes.firstOrNull { it.isDefault }?.url
                ?: network.rpcUrl
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

        throw unsupported("Solana transfers require mnemonic root material for ${chain.name}")
    }

    private fun requireMnemonicMatchesSelectedWallet(
        mnemonic: String,
        context: SolanaTransferContext
    ) {
        val derivedAddress = runCatching {
            SolanaKeyDerivation.deriveAccount(mnemonic).address
        }.getOrElse {
            throw unsupported("Solana mnemonic root material is invalid for ${chain.name}")
        }

        if (derivedAddress != context.sourceAddress) {
            throw unsupported("Solana mnemonic does not match selected wallet for ${chain.name}")
        }
    }

    private fun Transfer.amountLamports(): Long {
        return amountRawUnits("lamport")
    }

    private fun Transfer.amountTokenUnits(): Long {
        return amountRawUnits("token raw unit")
    }

    private fun Transfer.amountRawUnits(unitName: String): Long {
        return try {
            amountInPlanks.toLongExact()
        } catch (_: ArithmeticException) {
            throw unsupported("Solana transfer amount is outside the supported $unitName range")
        }
    }

    private suspend fun resolveTokenTransferContext(
        transfer: Transfer,
        context: SolanaTransferContext
    ): SolanaTokenTransferContext {
        val rawAmount = transfer.amountTokenUnits()
        val balance = balanceSync.balances(
            wallet = context.sourceAddress,
            network = context.network,
            baseUrl = context.indexerBaseUrl,
            includeTokenMetadata = false
        ).tokenBalances.firstOrNull { it.matchesAsset(transfer.chainAsset) }
            ?: throw unsupported("Solana token balance is unavailable for ${transfer.chainAsset.symbol} on ${chain.name}")

        if (balance.tokenProgram != SPL_TOKEN_PROGRAM_NAME) {
            throw unsupported("Solana token transfers currently support only SPL Token assets on ${chain.name}")
        }

        val sourceTokenAccount = balance.tokenAccountId?.takeIf(String::isNotBlank)
            ?: throw unsupported("Solana token source account is unavailable for ${transfer.chainAsset.symbol} on ${chain.name}")
        val mintAddress = balance.contractAddress?.takeIf(String::isNotBlank)
            ?: balance.assetId.takeIf(String::isNotBlank)
            ?: throw unsupported("Solana token mint is unavailable for ${transfer.chainAsset.symbol} on ${chain.name}")

        return SolanaTokenTransferContext(
            sourceTokenAccount = sourceTokenAccount,
            mintAddress = mintAddress,
            decimals = balance.decimals,
            rawAmount = rawAmount
        )
    }

    private fun UniversalWalletIndexedAssetBalance.matchesAsset(asset: Asset): Boolean {
        if (isNative || decimals != asset.precision) {
            return false
        }

        val assetIds = asset.solanaTokenIdentifiers()
        return assetIds.any { it == assetId || it == contractAddress }
    }

    private fun Asset.solanaTokenIdentifiers(): Set<String> {
        return listOf(id, currencyId)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun Asset.isSolanaNativeAsset(network: UniversalWalletRegistry.SolanaNetwork): Boolean {
        return isNative == true &&
            id == network.nativeAsset.id &&
            symbol == network.nativeAsset.symbol &&
            precision == network.nativeAsset.decimals
    }

    private fun solanaFeeAmountFromLamports(
        lamports: Long,
        network: UniversalWalletRegistry.SolanaNetwork
    ): BigDecimal {
        return chain.utilityAsset
            ?.takeIf { it.isSolanaNativeAsset(network) }
            ?.amountFromPlanks(BigInteger.valueOf(lamports))
            ?: BigDecimal.valueOf(lamports).movePointLeft(network.nativeAsset.decimals)
    }

    private fun String.normalizeMnemonic(): String? {
        val words = trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun unsupported(message: String): IllegalStateException {
        return IllegalStateException(message)
    }

    private data class SolanaTransferContext(
        val metaAccount: MetaAccount,
        val sourceAddress: String,
        val network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork,
        val indexerBaseUrl: String?,
        val rpcUrl: String
    )

    private data class SolanaTokenTransferContext(
        val sourceTokenAccount: String,
        val mintAddress: String,
        val decimals: Int,
        val rawAmount: Long
    )

    private companion object {
        const val SPL_TOKEN_PROGRAM_NAME = "spl-token"
    }
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
