package jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin

import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToOperationModel
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToParcel
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val historyFiltersProvider: HistoryFiltersProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val assetPayloadStateFlow: MutableStateFlow<AssetPayload>
) : TransactionHistoryMixin, CoroutineScope by CoroutineScope(Dispatchers.Default) {

    companion object {
        const val PAGE_SIZE = 100
        const val NEXT_PAGE_LOADING_OFFSET = PAGE_SIZE / 2
    }

    private var nextCursor: String? = null

    private val currentData: MutableSet<Operation> = mutableSetOf()

    private val _state = MutableStateFlow<TransactionHistoryUi.State>(TransactionHistoryUi.State.Refreshing)
    override fun state(): StateFlow<TransactionHistoryUi.State> = _state

    private val _sideEffects: MutableSharedFlow<TransactionHistoryUi.SideEffect> = MutableSharedFlow()
    override fun sideEffects() = _sideEffects

    init {
        launch {
            assetPayloadStateFlow.onEach {
                reloadHistory()
            }.launchIn(this)

            historyFiltersProvider.filtersFlow().onEach {
                reloadHistory()
            }.launchIn(this)
        }
    }

    private suspend fun reloadHistory() {
        nextCursor = null
        currentData.clear()
        _state.emit(TransactionHistoryUi.State.EmptyProgress)

        syncFirstOperationsPage(assetPayloadStateFlow.value)
        val cached = awaitOperationsFirstPage(assetPayloadStateFlow.value)
        nextCursor = cached.nextCursor

        if (cached.items.isEmpty()) {
            _state.emit(TransactionHistoryUi.State.Empty())
        } else {
            currentData.addAll(cached.items)
            _state.emit(TransactionHistoryUi.State.Data(transformData(cached.items)))
        }
    }

    private suspend fun awaitOperationsFirstPage(assetPayload: AssetPayload): CursorPage<Operation> {
        return walletInteractor.operationsFirstPageFlow(assetPayload.chainId, assetPayload.chainAssetId)
            .distinctUntilChangedBy { it.cursorPage }
            .map { it.cursorPage }
            .first()
    }

    override suspend fun syncFirstOperationsPage(assetPayload: AssetPayload): Result<*> {
        return walletInteractor.syncOperationsFirstPage(
            chainId = assetPayload.chainId,
            chainAssetId = assetPayload.chainAssetId,
            pageSize = PAGE_SIZE,
            filters = historyFiltersProvider.currentFilters()
        ).onFailure { throwable ->
            val message = when (throwable) {
                is HistoryNotSupportedException -> resourceManager.getString(R.string.wallet_transaction_history_unsupported_message)
                else -> resourceManager.getString(R.string.wallet_transaction_history_error_message)
            }
            _sideEffects.emit(TransactionHistoryUi.SideEffect.Error(throwable.localizedMessage ?: throwable.localizedMessage))
            _state.emit(TransactionHistoryUi.State.Empty(message))
        }
    }

    var nextPageLoading = false

    override fun scrolled(currentIndex: Int, assetPayload: AssetPayload) {
        val pageLoaded = currentData.size / PAGE_SIZE
        val currentPage = (currentIndex / PAGE_SIZE) + 1
        val currentPageOffset = (PAGE_SIZE * currentPage) - NEXT_PAGE_LOADING_OFFSET
        val isIndexEnoughToLoadNextPage = currentIndex >= currentPageOffset
        val isNextPageLoaded = pageLoaded > currentPage
        val hasDataToLoad = nextCursor != null

        if (hasDataToLoad.not() ||
            isIndexEnoughToLoadNextPage.not() ||
            isNextPageLoaded ||
            nextPageLoading
        ) {
            return
        }

        launch {
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
                    _sideEffects.emit(TransactionHistoryUi.SideEffect.Error(it.localizedMessage ?: it.localizedMessage))
                }.onSuccess {
                    nextCursor = it.nextCursor
                    if (it.isEmpty()) {
                        return@onSuccess
                    }
                    currentData.addAll(it.items)
                    _state.emit(TransactionHistoryUi.State.Data(transformData(currentData)))
                    nextPageLoading = false
                }
        }
    }

    override fun transactionClicked(transactionModel: OperationModel, assetPayload: AssetPayload) {
        launch {
            val operations = currentData

            val clickedOperation = operations.first { it.id == transactionModel.id }

            withContext(Dispatchers.Main) {
                when (val operation = mapOperationToParcel(clickedOperation, resourceManager)) {
                    is OperationParcelizeModel.Transfer -> {
                        router.openTransferDetail(operation, assetPayload)
                    }

                    is OperationParcelizeModel.Extrinsic -> {
                        router.openExtrinsicDetail(ExtrinsicDetailsPayload(operation, assetPayload.chainId))
                    }

                    is OperationParcelizeModel.Reward -> {
                        router.openRewardDetail(RewardDetailsPayload(operation, assetPayload.chainId))
                    }

                    is OperationParcelizeModel.Swap -> {
                        router.openSwapDetail(operation)
                    }
                }
            }
        }
    }

    private suspend fun transformData(data: Collection<Operation>): List<Any> {
        val accountIdentifier = addressDisplayUseCase.createIdentifier()

        val operations = data.map {
            mapOperationToOperationModel(it, accountIdentifier, resourceManager, iconGenerator)
        }

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
