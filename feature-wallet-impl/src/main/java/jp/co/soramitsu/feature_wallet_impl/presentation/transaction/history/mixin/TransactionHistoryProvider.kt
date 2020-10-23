package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.common.utils.zipSimilar
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement

private const val PAGE_SIZE = 20
private const val ICON_SIZE_DP = 32

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : TransactionHistoryMixin {

    override val transferHistoryDisposable = CompositeDisposable()

    private val _transactionsLiveData: MutableLiveData<List<Any>> = MutableLiveData()
    override val transactionsLiveData: LiveData<List<Any>> = _transactionsLiveData

    private var transactionsErrorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    private var transactionsSyncedInterceptor: Interceptor? = null

    private var currentTransactions: List<TransactionHistoryElement> = emptyList()

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

    override fun transactionClicked(transactionModel: TransactionModel) {
        router.openTransactionDetail(transactionModel)
    }

    private fun observeFirstPage() {
        currentPage = 0
        isLoading = true

        transferHistoryDisposable += walletInteractor.observeTransactionsFirstPage(PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .doOnNext { lastPageLoaded = false }
            .flatMapSingle { transformNewPage(it, true) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
                currentPage = 0
            }, transactionsErrorHandler)
    }

    private fun maybeLoadNewPage() {
        if (isLoading || lastPageLoaded) return

        currentPage++
        isLoading = true

        transferHistoryDisposable += walletInteractor.getTransactionPage(PAGE_SIZE, currentPage)
            .subscribeOn(Schedulers.io())
            .doOnSuccess { lastPageLoaded = it.isEmpty() }
            .flatMap { transformNewPage(it, false) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _transactionsLiveData.value = it
                isLoading = false
            }, {
                rollbackPageLoading()
            })
    }

    private fun rollbackPageLoading() {
        currentPage--
        isLoading = false
    }

    private fun transformNewPage(page: List<Transaction>, reset: Boolean): Single<List<Any>> {
        val models = page.map(::mapTransactionToTransactionModel)

        val historyElements = models.map { model ->
            createIcon(model.displayAddress).map { TransactionHistoryElement(it, model) }
        }

        return historyElements.zipSimilar()
            .map { elements -> elements.filter(filters) }
            .map { filtered -> regroup(filtered, reset) }
    }

    private fun regroup(newPage: List<TransactionHistoryElement>, reset: Boolean): List<Any> {
        val all = if (reset) newPage else currentTransactions + newPage

        currentTransactions = all.distinctBy { it.transactionModel.hash }

        return currentTransactions.groupBy { it.transactionModel.date.daysFromMillis() }
            .map { (daysSinceEpoch, transactions) ->
                val header = DayHeader(daysSinceEpoch)

                listOf(header) + transactions
            }.flatten()
    }

    private fun createIcon(address: String): Single<AddressModel> {
        return walletInteractor.getAddressId(address)
            .flatMap { iconGenerator.createAddressIcon(address, it, ICON_SIZE_DP) }
    }

    private fun List<TransactionHistoryElement>.filter(filters: List<TransactionFilter>): List<TransactionHistoryElement> {
        return filter { item -> filters.all { filter -> filter.shouldInclude(item) } }
    }
}