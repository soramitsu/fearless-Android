package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.core.utils.toLongExact
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.send.ReviewedWalletSend
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastOptions
import jp.co.soramitsu.common.data.network.solana.SolanaPaymentPlanner
import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaSimulationOptions
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

/** RPC-only SOL/SPL execution with reviewed fee/rent, exact bytes and durable signature recovery. */
class SolanaTransferService(
    private val chain: Chain,
    accountRepository: AccountRepository,
    private val rpcClient: SolanaRpcClient,
    private val quotes: NativeSendQuoteStore,
    private val executor: NativeSendExecutor,
    pausePolicy: SendPausePolicy,
    private val pollDelayMillis: Long = 500L,
    private val network: UniversalWalletRegistry.SolanaNetwork = chain.universalWalletSolanaIndexerNetwork()
        ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT),
    private val currentChain: suspend () -> Chain = { chain }
) : TransferService {
    private val networkIdentity = SolanaSendNetworkIdentity(requireNotNull(chain.universalWalletSolanaIndexerNetwork()).genesisHash, network.genesisHash)
    private val guard = NativeSendAccountGuard(chain, accountRepository, pausePolicy, networkIdentity, currentChain)
    private suspend fun currentRpcUrls(): List<String> {
        val latest = currentChain()
        if (latest.id != chain.id || latest.universalWalletSolanaIndexerNetwork()?.genesisHash != networkIdentity.registryGenesisHash) {
            throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        }
        return NativeSendRpcEndpoints.resolve(latest)
    }

    override suspend fun getTransferFee(transfer: Transfer): BigDecimal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        quote(transfer).review.totalFee
    }

    override suspend fun getTransferMaxAmount(transfer: Transfer): BigDecimal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        quote(transfer.copy(sendMax = true)).review.amount
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> = flow { emit(getTransferFee(transfer)) }

    private suspend fun quote(transfer: Transfer): NativeSendQuoteStore.Quote.Solana {
        val binding = guard.bind(transfer)
        val create: suspend () -> NativeSendQuoteStore.Quote.Solana = {
            val endpoints = currentRpcUrls()
            val planner = SolanaPaymentPlanner(rpcClient.withGenesisHash(network.genesisHash).withEndpoints(endpoints))
            val native = transfer.chainAsset.isNative == true && transfer.chainAsset.id == network.nativeAsset.id && binding.precision == 9
            val plan = if (native) {
                planner.native(binding.sender, binding.recipient, transfer.amountInPlanks.toLongExact(), sendMax = transfer.sendMax)
            } else {
                if (transfer.chainAsset.isNative == true) throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
                val mint = binding.currencyId?.takeIf { it.isNotBlank() } ?: binding.assetId
                SolanaPublicKeyCodec.decode(mint)
                planner.token(binding.sender, binding.recipient, mint, binding.precision, transfer.amountInPlanks, sendMax = transfer.sendMax)
            }
            guard.requireCurrent(binding)
            NativeSendQuoteStore.Quote.Solana(binding, NativeSendReview(BigDecimal(plan.amount).movePointLeft(binding.precision),
                BigDecimal.valueOf(plan.networkFeeLamports, 9), BigDecimal.valueOf(plan.accountRentLamports, 9),
                lastValidBlockHeight = plan.blockhash.lastValidBlockHeight), plan, endpoints)
        }
        return (transfer.quoteId?.let { quotes.getOrCreate(it, binding, create) } ?: create()) as NativeSendQuoteStore.Quote.Solana
    }

    override suspend fun transfer(transfer: Transfer): String {
        val binding = guard.bind(transfer)
        // A restarted retry may use the registry's current same-chain provider, but never new bytes.
        var client = rpcClient.withGenesisHash(network.genesisHash).withEndpoints(currentRpcUrls())
        return executor.execute(binding, transfer.quoteId, PendingWalletSend.Kind.Solana, { guard.requireCurrent(binding) }, prepare = {
            val quoted = quotes.requireQuote(transfer.quoteId, binding) as? NativeSendQuoteStore.Quote.Solana
                ?: throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
            if (transfer.amount.compareTo(quoted.review.amount) != 0 || transfer.estimateFee?.compareTo(quoted.review.totalFee) != 0) {
                throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
            }
            client = rpcClient.withGenesisHash(network.genesisHash).withEndpoints(quoted.rpcUrls)
            val planner = SolanaPaymentPlanner(client)
            planner.revalidate(quoted.plan)
            val signed = SolanaTransactionSigner.signSerializedTransaction(guard.mnemonic(binding), quoted.plan.message.transactionBase64,
                expectedSigner = binding.sender)
            val simulation = client.simulateTransaction(signed.signedTransactionBase64,
                SolanaSimulationOptions(replaceRecentBlockhash = false, sigVerify = true))
            if (simulation.value.errorJson != null) throw NativeSendException(NativeSendException.Code.SIMULATION_FAILED)
            planner.requireUnexpired(quoted.plan)
            guard.requireCurrent(binding)
            PendingWalletSend(WalletSendJournal.scopeHash(binding.scope), requireNotNull(transfer.quoteId), binding.fingerprint,
                PendingWalletSend.Kind.Solana, signed.signatureBase58, signed.signedTransactionBase64,
                quoted.plan.networkFeeLamports.toString(), quoted.plan.accountRentLamports.toString(),
                quoted.plan.blockhash.lastValidBlockHeight, System.currentTimeMillis(), reviewed = ReviewedWalletSend(chain.id, chain.name, binding.assetId, binding.currencyId,
                    transfer.chainAsset.symbol, binding.precision, binding.sender, binding.recipient, quoted.plan.amount.toString()),
                solanaNetwork = networkIdentity)
        }, submit = { retained ->
            requireRetainedNetwork(retained, binding)
            // Expired ambiguity stays retained for history polling; expiry cannot prove non-inclusion.
            if (client.blockHeight() <= requireNotNull(retained.lastValidBlockHeight)) {
                guard.requireCurrent(binding)
                val signature = client.sendRawTransaction(retained.signedPayload, SolanaBroadcastOptions(maxRetries = 3))
                require(signature == retained.transactionId)
            }
        }, status = { retained ->
            requireRetainedNetwork(retained, binding)
            var result: PendingWalletSend.State? = null
            repeat(3) { attempt ->
                if (result == null) {
                    guard.requireCurrent(binding)
                    val observed = client.signatureStatus(retained.transactionId)
                    guard.requireCurrent(binding)
                    if (observed?.isConfirmed == true) result = if (observed.errorJson == null) PendingWalletSend.State.Confirmed else PendingWalletSend.State.Failed
                    if (result == null && attempt < 2) delay(pollDelayMillis)
                }
            }
            guard.requireCurrent(binding)
            result
        })
    }
    private suspend fun requireRetainedNetwork(payment: PendingWalletSend, binding: NativeSendBinding) {
        if (payment.kind != PendingWalletSend.Kind.Solana || payment.solanaNetwork != networkIdentity) {
            throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
        }
        guard.requireCurrent(binding)
    }
}
