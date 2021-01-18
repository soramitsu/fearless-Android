package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapTransactionToTransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 20

private const val SCROLL_OFFSET = PAGE_SIZE

private const val ICON_SIZE_DP = 32

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter
) : TransactionHistoryMixin {

    override val transactionsLiveData = MutableLiveData<List<Any>>()

    private var currentTransactions: List<TransactionHistoryElement> = emptyList()

    private var currentPage: Int = 0
    private var isLoading = false
    private var lastPageLoaded = false

    private val filters: MutableList<TransactionFilter> = mutableListOf()

    override fun startObservingTransactions(scope: CoroutineScope) {
        currentPage = 0
        isLoading = true


        walletInteractor.transactionsFirstPageFlow(PAGE_SIZE)
            .map { transformNewPage(it, true) }
            .onEach {
                lastPageLoaded = false
                isLoading = false
                currentPage = 0

                transactionsLiveData.value = it
            }.launchIn(scope)
    }

    override fun scrolled(scope: CoroutineScope, currentIndex: Int) {
        val currentSize = transactionsLiveData.value?.size ?: return

        if (currentIndex >= currentSize - SCROLL_OFFSET) {
            maybeLoadNewPage(scope)
        }
    }

    override fun addFilter(scope: CoroutineScope, filter: TransactionFilter) {
        filters += filter

        scope.launch {
            val filtered = withContext(Dispatchers.Default) {
                currentTransactions.filter(filters)
            }

            transactionsLiveData.value = filtered
        }
    }

    override fun clear() {
        filters.clear()
    }

    override suspend fun syncFirstTransactionsPage(): Result<Unit> {
        return walletInteractor.syncTransactionsFirstPage(PAGE_SIZE)
    }

    override fun transactionClicked(transactionModel: TransactionModel) {
        router.openTransactionDetail(transactionModel)
    }

    private fun maybeLoadNewPage(scope: CoroutineScope) {
        if (isLoading || lastPageLoaded) return

        currentPage++
        isLoading = true

        scope.launch {
            val result = walletInteractor.getTransactionPage(PAGE_SIZE, currentPage)

            if (result.isSuccess) {
                val newPage = result.getOrThrow()

                lastPageLoaded = newPage.isEmpty()

                val combined = transformNewPage(newPage, false)

                transactionsLiveData.value = combined
            } else {
                rollbackPageLoading()
            }

            isLoading = false
        }
    }

    private fun rollbackPageLoading() {
        currentPage--
    }

    private suspend fun transformNewPage(page: List<Transaction>, reset: Boolean): List<Any> = withContext(Dispatchers.Default) {
        val models = page.map(::mapTransactionToTransactionModel)

        val filteredHistoryElements = models.map { model ->
            val addressModel = createIcon(model.displayAddress)

            TransactionHistoryElement(addressModel, model)
        }.filter(filters)

        regroup(filteredHistoryElements, reset)
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

    private suspend fun createIcon(address: String): AddressModel {
        return iconGenerator.createAddressModel(address, ICON_SIZE_DP)
    }

    private fun List<TransactionHistoryElement>.filter(filters: List<TransactionFilter>): List<TransactionHistoryElement> {
        return filter { item -> filters.all { filter -> filter.shouldInclude(item) } }
    }
}