package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Filter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.NewTransactionHistoryElement
import kotlinx.coroutines.CoroutineScope

interface TransactionFilter : Filter<NewTransactionHistoryElement>

interface TransactionHistoryUi {
    val transactionsLiveData: LiveData<List<Any>>

    fun transactionClicked(transactionModel: TransactionModel)
}

interface TransactionHistoryMixin : TransactionHistoryUi {

    suspend fun syncFirstTransactionsPage(): Result<String?>

    fun scrolled(scope: CoroutineScope, currentIndex: Int)

    fun startObservingTransactions(scope: CoroutineScope)

    fun addFilter(scope: CoroutineScope, filter: TransactionFilter)

    fun clear()
}
