package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.utils.applyFilters
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapHistoryElementToTransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.NewTransactionHistoryElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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

    private var currentTransactions: List<NewTransactionHistoryElement> = emptyList()

    private var currentPage: Int = 0
    private var isLoading = false
    private var lastPageLoaded = false

    private val filters: MutableList<TransactionFilter> = mutableListOf()

    override fun startObservingTransactions(scope: CoroutineScope) {
        currentPage = 0
        isLoading = true

        walletInteractor.newTransactionsFirstPageFlow()
            .map { newTransformNewPage(it, true) }
            .flowOn(Dispatchers.Default)
            .onEach {
                lastPageLoaded = false
                isLoading = false
                currentPage = 0
                println("------- ON EACH $it ${it.size}")
                println("------- ON EACH ${it.size}")

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
                currentTransactions.applyFilters(filters)
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
            val result = walletInteractor.getNewTransactions(PAGE_SIZE, currentPage)
            println("------- result: $result $PAGE_SIZE $currentPage")
//            val newPage = result.map {mapHistoryElementToTransactionModel(it) }
//
//            lastPageLoaded = result.isEmpty()
//            val combined = newTransformNewPage(result, false)
//            transactionsLiveData.value = combined
            if (result.isSuccess) {
                val newPage = result.getOrThrow()

                lastPageLoaded = newPage.isEmpty()

                val combined = newTransformNewPage(newPage, false)

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

    private suspend fun newTransformNewPage(page: List<SubqueryElement>, reset: Boolean): List<Any> = withContext(Dispatchers.Default) {
        val filteredHistoryElements = page.map { transaction ->
            val addressModel = createIcon(transaction.address, transaction.accountName)

            NewTransactionHistoryElement(addressModel, transaction)
        }.applyFilters(filters)

        newRegroup(filteredHistoryElements, reset)
    }

    private fun newRegroup(newPage: List<NewTransactionHistoryElement>, reset: Boolean): List<Any> {
        currentTransactions = if (reset) newPage else currentTransactions + newPage

        return currentTransactions.groupBy { it.transactionModel.time.daysFromMillis() }
            .map { (daysSinceEpoch, transactions) ->
                val header = DayHeader(daysSinceEpoch)

                listOf(header) + transactions
            }.flatten()
    }

    private suspend fun createIcon(address: String, accountName: String?): AddressModel {
        return iconGenerator.createAddressModel(address, ICON_SIZE_DP, accountName)
    }
}
