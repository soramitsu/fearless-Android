package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable
import jp.co.soramitsu.common.utils.ErrorHandler

typealias Interceptor = () -> Unit

interface TransferHistoryMixin {
    val transferHistoryDisposable: Disposable

    val transactionsLiveData: LiveData<List<Any>>

    fun setTransactionErrorHandler(handler: ErrorHandler)

    fun setTransactionSyncedInterceptor(interceptor: Interceptor)

    fun shouldLoadPage()

    fun syncFirstTransactionsPage()
}