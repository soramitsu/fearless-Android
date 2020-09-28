package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable

interface TransferHistoryMixin {
    val transferHistoryDisposable: Disposable

    val transactionsLiveData: LiveData<List<Any>>

    fun shouldLoadPage()

    fun syncFirstTransactionsPage()
}