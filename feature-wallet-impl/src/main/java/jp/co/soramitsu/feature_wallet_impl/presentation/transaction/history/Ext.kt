package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history

import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionHistoryUi.State

fun TransferHistorySheet.showState(state: State) {
    when (state) {
        is State.Empty -> showPlaceholder()
        is State.EmptyProgress -> showProgress()
        is State.Data -> showTransactions(state.items)
    }
}
