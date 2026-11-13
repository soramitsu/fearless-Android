package jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin

import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToOperationModel
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToTransactionDetailsState
import jp.co.soramitsu.wallet.impl.data.network.subquery.ExplorerOnlyHistoryException
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val historyFiltersProvider: HistoryFiltersProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val assetPayloadStateFlow: MutableStateFlow<AssetPayload>
) : TransactionHistoryMixin,
    // The history is one panel of the asset page. A failure in it must leave the page running: without a handler a
    // failure here was uncaught and closed the app.
    CoroutineScope by CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, _ -> }) {

    companion object {
        const val PAGE_SIZE = 100
        const val NEXT_PAGE_LOADING_OFFSET = PAGE_SIZE / 2
    }

    private var nextCursor: String? = null

    // Set while the network's history service cannot serve this account; the explorer link stands in for the list.
    @Volatile private var explorerOnlyMessage: String? = null

    // One entry per operation id. The list keys its rows by id and a repeated key closes the app, so a later copy
    // of an operation (a pending transfer that completed, a row the next page repeats) replaces the earlier one.
    private val currentData = ConcurrentHashMap<String, Operation>()

    private val _state =
        MutableStateFlow<TransactionHistoryUi.State>(TransactionHistoryUi.State.Refreshing)

    private val reloadHistoryEvent = MutableSharedFlow<Unit>()

    override fun state(): StateFlow<TransactionHistoryUi.State> = _state

    private val _sideEffects: MutableSharedFlow<TransactionHistoryUi.SideEffect> =
        MutableSharedFlow()

    override fun sideEffects() = _sideEffects

    init {
        reloadHistoryEvent.debounce(100).onEach {
            try {
                reloadHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // getChain gives up after 60 s when the network left the catalogue or never synced (229 crash).
                _state.emit(TransactionHistoryUi.State.Empty(
                    resourceManager.getString(R.string.wallet_transaction_history_error_message)
                ))
            }
        }.launchIn(this)

        assetPayloadStateFlow.onEach {
            reloadHistoryEvent.emit(Unit)
        }.launchIn(this)

        historyFiltersProvider.filtersFlow().onEach {
            reloadHistoryEvent.emit(Unit)
        }.launchIn(this)
    }

    suspend fun tryReloadHistory() {
        reloadHistoryEvent.emit(Unit)
    }

    private var operationsObserveJob: Job? = null

    private suspend fun reloadHistory() {
        nextPageLoading = false
        nextCursor = null
        explorerOnlyMessage = null
        currentData.clear()
        _state.emit(TransactionHistoryUi.State.EmptyProgress)
        val asset = assetPayloadStateFlow.value

        val ecosystem = walletInteractor.getChain(asset.chainId).ecosystem
        syncFirstOperationsPage(asset)
        operationsObserveJob?.cancel()
        operationsObserveJob = observeOperationsFirstPage(asset).onEach { page ->
            nextCursor = page.nextCursor
            val filtersApplied = historyFiltersProvider.currentFilters()
            val filteredPageItems = page.items.filter {
                when (it.type) {
                    is Operation.Type.Extrinsic -> filtersApplied.contains(TransactionFilter.EXTRINSIC)
                    is Operation.Type.Reward -> filtersApplied.contains(TransactionFilter.REWARD)
                    is Operation.Type.Swap -> filtersApplied.contains(TransactionFilter.EXTRINSIC)
                    is Operation.Type.Transfer -> filtersApplied.contains(TransactionFilter.TRANSFER)
                }
            }

            if (filteredPageItems.isEmpty()) {
                _state.emit(explorerOnlyMessage?.let { TransactionHistoryUi.State.Empty(it, explorerOnly = true) }
                    ?: TransactionHistoryUi.State.Empty())
            } else {
                currentData.putAll(page.items.associateBy(Operation::id))
                _state.emit(TransactionHistoryUi.State.Data(transformData(filteredPageItems, ecosystem)))
            }
        }.launchIn(this)
    }

    private fun observeOperationsFirstPage(assetPayload: AssetPayload): Flow<CursorPage<Operation>> {
        return walletInteractor.operationsFirstPageFlow(
            assetPayload.chainId,
            assetPayload.chainAssetId
        )
            .distinctUntilChangedBy { it.cursorPage }
            .map { it.cursorPage }
            .catch {
                emit(CursorPage(null, listOf()))
            }
    }

    private var firstPageSyncJob: Job? = null

    override suspend fun syncFirstOperationsPage(assetPayload: AssetPayload) {
        if (firstPageSyncJob?.isActive == true || firstPageSyncJob?.isCompleted == false) return

        firstPageSyncJob = coroutineScope {
            launch {
                walletInteractor.syncOperationsFirstPage(
                    chainId = assetPayload.chainId,
                    chainAssetId = assetPayload.chainAssetId,
                    pageSize = PAGE_SIZE,
                    filters = historyFiltersProvider.currentFilters()
                ).onFailure { throwable ->
                    if (throwable is ExplorerOnlyHistoryException) {
                        // Not an error to report: the explorer has this account's history.
                        val message = resourceManager.getString(R.string.wallet_transaction_history_unsupported_message)
                        explorerOnlyMessage = message
                        if (currentData.isEmpty()) _state.emit(TransactionHistoryUi.State.Empty(message, explorerOnly = true))
                        return@onFailure
                    }
                    val message = when (throwable) {
                        is HistoryNotSupportedException -> resourceManager.getString(R.string.wallet_transaction_history_unsupported_message)
                        else -> resourceManager.getString(R.string.wallet_transaction_history_error_message)
                    }
                    _sideEffects.emit(
                        TransactionHistoryUi.SideEffect.Error(
                            throwable.localizedMessage ?: message
                        )
                    )
                    if (currentData.isEmpty()) {
                        _state.emit(TransactionHistoryUi.State.Empty(message))
                    }
                }.onSuccess {
                    explorerOnlyMessage = null
                    nextCursor = it.nextCursor
                }

                firstPageSyncJob?.cancel()
            }
        }

    }

    private var nextPageLoading = false

    override fun scrolled(currentIndex: Int, assetPayload: AssetPayload) {
        val pageLoaded = currentData.size / PAGE_SIZE
        val currentPage = (currentIndex / PAGE_SIZE) + 1
        val currentPageOffset = (PAGE_SIZE * currentPage) - NEXT_PAGE_LOADING_OFFSET
        val hasDataToLoad = nextCursor != null
        val isNextPageLoaded = pageLoaded > currentPage
        val isIndexEnoughToLoadNextPage = currentIndex >= currentPageOffset

        if (hasDataToLoad.not() ||
            isIndexEnoughToLoadNextPage.not() ||
            isNextPageLoaded ||
            nextPageLoading
        ) {
            return
        }

        launch {
            try {
                loadNextPage(assetPayload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The loaded rows stay; the next scroll may try again.
                nextPageLoading = false
            }
        }
    }

    private suspend fun loadNextPage(assetPayload: AssetPayload) {
        nextPageLoading = true
        walletInteractor.getOperations(
            assetPayload.chainId,
            assetPayload.chainAssetId,
            PAGE_SIZE,
            nextCursor,
            historyFiltersProvider.currentFilters()
        )
            .onFailure {
                nextPageLoading = false
                if (it is ExplorerOnlyHistoryException) {
                    // Later pages became unavailable: stop paging quietly; the explorer link remains.
                    nextCursor = null
                    return@onFailure
                }
                _sideEffects.emit(
                    TransactionHistoryUi.SideEffect.Error(
                        it.localizedMessage ?: it.message
                    )
                )
            }.onSuccess {
                nextCursor = it.nextCursor
                nextPageLoading = false
                if (it.isEmpty()) {
                    return@onSuccess
                }
                currentData.putAll(it.items.associateBy(Operation::id))
                val ecosystem = walletInteractor.getChain(assetPayload.chainId).ecosystem
                _state.emit(TransactionHistoryUi.State.Data(transformData(
                    currentData.values,
                    ecosystem
                )))
            }
    }

    override suspend fun getTransactionDetailsState(
        transactionModel: OperationModel,
        assetPayload: AssetPayload
    ): TransactionDetailsState {
        val clickedOperation = currentData[transactionModel.id]
            ?: throw IllegalStateException("Cannot find operation in cache")

        val chain = walletInteractor.getChain(assetPayload.chainId)

        return mapOperationToTransactionDetailsState(
            clickedOperation,
            resourceManager,
            iconGenerator,
            chain
        )
    }

    private suspend fun transformData(
        data: Collection<Operation>,
        ecosystem: Ecosystem
    ): List<Any> {
        val operations = data.latestById().map {
            mapOperationToOperationModel(it, resourceManager, iconGenerator, ecosystem)
        }.sortedByDescending { it.time }

        return regroup(operations)
    }

    private fun regroup(operations: List<OperationModel>): List<Any> {
        return operations.groupBy { it.time.daysFromMillis() }
            .map { (daysSinceEpoch, transactions) ->
                val header = DayHeader(daysSinceEpoch)

                listOf(header) + transactions
            }.flatten()
    }
}

/**
 * One operation per id, the last copy winning. An explorer can list one transaction twice (two token transfers in it,
 * or the same row on two pages), and the history list refuses two rows with one key.
 */
internal fun Collection<Operation>.latestById(): List<Operation> = associateBy(Operation::id).values.toList()
