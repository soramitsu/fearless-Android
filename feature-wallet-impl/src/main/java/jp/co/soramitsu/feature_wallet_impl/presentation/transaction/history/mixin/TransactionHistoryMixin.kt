package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement

typealias Interceptor = () -> Unit

interface TransactionFilter {
    fun shouldInclude(model: TransactionHistoryElement): Boolean
}

interface TransactionHistoryUi {
    val transactionsLiveData: LiveData<List<Any>>

    fun scrolled(currentIndex: Int)

    fun syncFirstTransactionsPage()

    fun transactionClicked(transactionModel: TransactionModel)
}

interface TransactionHistoryMixin : TransactionHistoryUi {
    val transferHistoryDisposable: Disposable

    fun setTransactionErrorHandler(handler: ErrorHandler)

    fun setTransactionSyncedInterceptor(interceptor: Interceptor)

    fun addFilter(filter: TransactionFilter)

    fun clear()
}