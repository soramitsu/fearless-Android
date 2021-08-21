package jp.co.soramitsu.feature_wallet_impl.presentation

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilter

interface WalletRouter {
    fun openAssetDetails(type: Token.Type)

    fun back()

    fun openChooseRecipient()

    fun openFilter()

    val filterList: List<HistoryFilter>?

    fun setHistoryFilter(historyFilter: List<HistoryFilter>)

    fun openChooseAmount(recipientAddress: String)

    fun openConfirmTransfer(transferDraft: TransferDraft)

    fun finishSendFlow()

    fun openRepeatTransaction(recipientAddress: String)

    fun openTransactionDetail(transaction: TransactionModel)

    fun openAddAccount()

    fun openChangeAccountFromWallet()

    fun openReceive()
}
