package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.runtime.Composable
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapPreviewContent
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransferDetailScreen

@Composable
fun TransactionDetailsBottomSheet(transactionDetailsState: TransactionDetailsState, callback: TransactionDetailsBottomSheetCallback) {
    when(transactionDetailsState){
        is SwapDetailState -> {
            SwapPreviewContent(transactionDetailsState, callback)
        }
        is TransferDetailsState -> {
            TransferDetailScreen(transactionDetailsState, callback)
        }
    }
}