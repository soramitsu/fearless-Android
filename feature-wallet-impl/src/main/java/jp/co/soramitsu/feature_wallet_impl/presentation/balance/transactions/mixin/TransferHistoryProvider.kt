package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.toUI
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import java.util.concurrent.TimeUnit

private const val PAGE_SIZE = 20

class TransferHistoryProvider(private val walletInteractor: WalletInteractor) : TransferHistoryMixin {

    override val transferHistoryDisposable = CompositeDisposable()

    private val _transactionsLiveData: MutableLiveData<List<Any>> = MutableLiveData()
    override val transactionsLiveData: LiveData<List<Any>> = _transactionsLiveData

    private var currentTransactions: List<TransactionModel> = emptyList()

    private var currentPage: Int = 0
    private var isLoading = false
    private var lastPageLoaded = false

    init {
        observeFirstPage()
    }

    private fun observeFirstPage() {
        currentPage = 0
        isLoading = true

        transferHistoryDisposable += walletInteractor.observeTransactionsFirstPage(PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .doOnNext { lastPageLoaded = false }
            .map { it.map(Transaction::toUI) }
            .map { regroup(it, reset = true) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, DEFAULT_ERROR_HANDLER)
    }

    private fun maybeLoadNewPage() {
        if (isLoading || lastPageLoaded) return

        currentPage++
        isLoading = true

        transferHistoryDisposable += walletInteractor.getTransactionPage(PAGE_SIZE, currentPage)
            .subscribeOn(Schedulers.io())
            .doOnSuccess { lastPageLoaded = it.isEmpty() }
            .map { it.map(Transaction::toUI) }
            .map { regroup(it, reset = false) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, DEFAULT_ERROR_HANDLER)
    }

    private fun regroup(newPage: List<TransactionModel>, reset: Boolean): List<Any> {
        val all = if (reset) newPage else currentTransactions + newPage

        currentTransactions = all

        return all.groupBy { extractDay(it.date) }
            .map { (_, transactions) ->
                val millis = transactions.first().date

                val header = DayHeader(millis)

                listOf(header) + transactions
            }.flatten()
    }

    override fun shouldLoadPage() {
        maybeLoadNewPage()
    }

    override fun syncFirstTransactionsPage() {
        transferHistoryDisposable += walletInteractor.syncTransactionsFirstPage(PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeToError(DEFAULT_ERROR_HANDLER)
    }

    private fun extractDay(millis: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(millis)
    }
}