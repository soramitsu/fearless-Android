package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

typealias Interceptor = () -> Unit

interface TransactionFilter {
    fun shouldInclude(model: TransactionModel): Boolean
}

interface TransactionHistoryUi {
    val transactionsLiveData: LiveData<List<Any>>

    fun shouldLoadPage()

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