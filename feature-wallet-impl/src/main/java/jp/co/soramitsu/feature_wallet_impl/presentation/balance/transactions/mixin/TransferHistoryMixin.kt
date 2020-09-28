package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable
import jp.co.soramitsu.common.utils.ErrorHandler

interface TransferHistoryMixin {
    val transferHistoryDisposable: Disposable

    val transactionsLiveData: LiveData<List<Any>>

    var transactionsErrorHandler: ErrorHandler

    fun shouldLoadPage()

    fun syncFirstTransactionsPage()
}