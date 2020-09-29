package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.mixin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import java.util.concurrent.TimeUnit

private const val PAGE_SIZE = 20

class TransferHistoryProvider(private val walletInteractor: WalletInteractor) : TransferHistoryMixin {

    override val transferHistoryDisposable = CompositeDisposable()

    private val _transactionsLiveData: MutableLiveData<List<Any>> = MutableLiveData()
    override val transactionsLiveData: LiveData<List<Any>> = _transactionsLiveData

    private var transactionsErrorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    private var transactionsSyncedInterceptor: Interceptor? = null

    private var currentTransactions: List<TransactionModel> = emptyList()

    private var currentPage: Int = 0
    private var isLoading = false
    private var lastPageLoaded = false

    private val filters: MutableList<TransactionFilter> = mutableListOf()

    init {
        observeFirstPage()
    }

    override fun setTransactionErrorHandler(handler: ErrorHandler) {
        transactionsErrorHandler = handler
    }

    override fun setTransactionSyncedInterceptor(interceptor: Interceptor) {
        transactionsSyncedInterceptor = interceptor
    }

    override fun shouldLoadPage() {
        maybeLoadNewPage()
    }

    override fun addFilter(filter: TransactionFilter) {
        filters += filter

        transferHistoryDisposable += Single.just(currentTransactions)
            .subscribeOn(Schedulers.io())
            .map { list -> list.filter(filters) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
            }, transactionsErrorHandler)
    }

    override fun clear() {
        filters.clear()
    }

    override fun syncFirstTransactionsPage() {
        transferHistoryDisposable += walletInteractor.syncTransactionsFirstPage(PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { transactionsSyncedInterceptor?.invoke() }
            .subscribeToError(transactionsErrorHandler)
    }

    private fun observeFirstPage() {
        currentPage = 0
        isLoading = true

        transferHistoryDisposable += walletInteractor.observeTransactionsFirstPage(PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .doOnNext { lastPageLoaded = false }
            .map { it.map(::mapTransactionToTransactionModel) }
            .map { list -> list.filter(filters) }
            .map { regroup(it, reset = true) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, transactionsErrorHandler)
    }

    private fun maybeLoadNewPage() {
        if (isLoading || lastPageLoaded) return

        currentPage++
        isLoading = true

        transferHistoryDisposable += walletInteractor.getTransactionPage(PAGE_SIZE, currentPage)
            .subscribeOn(Schedulers.io())
            .doOnSuccess { lastPageLoaded = it.isEmpty() }
            .map { it.map(::mapTransactionToTransactionModel) }
            .map { list -> list.filter(filters) }
            .map { regroup(it, reset = false) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, transactionsErrorHandler)
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

    private fun extractDay(millis: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(millis)
    }

    private fun List<TransactionModel>.filter(filters: List<TransactionFilter>): List<TransactionModel> {
        return filter { item -> filters.all { filter -> filter.shouldInclude(item) } }
    }
}