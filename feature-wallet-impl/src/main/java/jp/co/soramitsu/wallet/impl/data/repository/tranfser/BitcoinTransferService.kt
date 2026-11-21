package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.core.utils.toLongExact
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlanner
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendRequest
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendService
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSpendableAccountScanner
import jp.co.soramitsu.common.data.network.send.ReviewedWalletSend
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

/** The production BTC path quotes every discovered source and signs only the reviewed transaction. */
class BitcoinTransferService(
    private val chain: Chain,
    accountRepository: AccountRepository,
    private val client: BitcoinIndexerClient,
    private val quotes: NativeSendQuoteStore,
    private val executor: NativeSendExecutor,
    pausePolicy: SendPausePolicy
) : TransferService {
    private val guard = NativeSendAccountGuard(chain, accountRepository, pausePolicy)
    private val network get() = chain.universalWalletBitcoinIndexerNetwork()
        ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
    private val derivationNetwork get() = if (network == BitcoinIndexerRoutes.Network.Mainnet) BitcoinKeyDerivation.Network.Mainnet else BitcoinKeyDerivation.Network.Testnet
    private val baseUrl get() = chain.externalApi?.history?.takeIf { it.type == Chain.ExternalApi.Section.Type.BITCOIN }?.url

    override suspend fun getTransferFee(transfer: Transfer): BigDecimal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        quote(transfer).review.totalFee
    }

    override suspend fun getTransferMaxAmount(transfer: Transfer): BigDecimal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        quote(transfer.copy(sendMax = true)).review.amount
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> = flow { emit(getTransferFee(transfer)) }

    private suspend fun quote(transfer: Transfer): NativeSendQuoteStore.Quote.Bitcoin {
        val binding = guard.bind(transfer)
        if (transfer.chainAsset.isNative != true || transfer.chainAsset.precision != 8) {
            throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
        }
        val rawAmount = transfer.amountInPlanks.toLongExact()
        if (!transfer.sendMax) require(rawAmount > 0)
        val create: suspend () -> NativeSendQuoteStore.Quote.Bitcoin = {
            val root = guard.root(binding)
            val account = BitcoinSpendableAccountScanner(client).scan(root.mnemonic, binding.sender, derivationNetwork, baseUrl,
                passphrase = root.passphrase)
            val plan = BitcoinSendPlanner(client).plan(rawAmount, account.sources,
                binding.recipient, account.nextChangeAddress, network = network, baseUrl = baseUrl, sendMax = transfer.sendMax)
            guard.requireCurrent(binding)
            NativeSendQuoteStore.Quote.Bitcoin(binding, NativeSendReview(BigDecimal.valueOf(plan.amountSats, 8),
                BigDecimal.valueOf(plan.feeSats, 8), satPerVbyte = plan.feeRateSatPerVbyte), plan, account.nextChangePath, baseUrl)
        }
        return (transfer.quoteId?.let { quotes.getOrCreate(it, binding, create) } ?: create()) as NativeSendQuoteStore.Quote.Bitcoin
    }

    override suspend fun transfer(transfer: Transfer): String {
        val binding = guard.bind(transfer)
        return executor.execute(binding, transfer.quoteId, PendingWalletSend.Kind.Bitcoin, { guard.requireCurrent(binding) }, prepare = {
            val quoted = quotes.requireQuote(transfer.quoteId, binding) as? NativeSendQuoteStore.Quote.Bitcoin
                ?: throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
            if (transfer.amount.compareTo(quoted.review.amount) != 0 || transfer.estimateFee?.compareTo(quoted.review.totalFee) != 0) {
                throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)
            }
            val root = guard.root(binding)
            quoted.plan.changeAddress?.let { changeAddress ->
                val change = BitcoinKeyDerivation.deriveKey(root.mnemonic, root.passphrase, quoted.changePath, derivationNetwork)
                require(change.address == changeAddress)
            }
            val signed = BitcoinSendService(client).prepareConfirmedPlan(BitcoinSendRequest(root.mnemonic, quoted.plan.amountSats,
                quoted.plan.selectedUtxos.map { jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource(requireNotNull(it.address), it.derivationPath) }.distinct(), binding.recipient,
                passphrase = root.passphrase, changeAddress = quoted.plan.changeAddress, network = network, baseUrl = quoted.baseUrl), quoted.plan)
            guard.requireCurrent(binding)
            PendingWalletSend(WalletSendJournal.scopeHash(binding.scope), requireNotNull(transfer.quoteId), binding.fingerprint,
                PendingWalletSend.Kind.Bitcoin, signed.transaction.txid, signed.transaction.txHex, quoted.plan.feeSats.toString(), "0", null,
                System.currentTimeMillis(), reviewed = ReviewedWalletSend(chain.id, chain.name, binding.assetId, binding.currencyId,
                    transfer.chainAsset.symbol, binding.precision, binding.sender, binding.recipient, quoted.plan.amountSats.toString()))
        }, submit = { retained ->
            val response = client.broadcastTransaction(retained.signedPayload, network, baseUrl).trim().lowercase()
            require(response == retained.transactionId)
        }, status = { retained ->
            client.transactionStatus(retained.transactionId, network, baseUrl)?.takeIf { it.confirmed }?.let { PendingWalletSend.State.Confirmed }
        })
    }
}
