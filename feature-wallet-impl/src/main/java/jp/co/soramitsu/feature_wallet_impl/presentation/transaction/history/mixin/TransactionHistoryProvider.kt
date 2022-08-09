package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToOperationModel
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToParcel
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionStateMachine.Action
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionStateMachine.State
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICON_SIZE_DP = 32

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val historyFiltersProvider: HistoryFiltersProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
) : TransactionHistoryMixin, CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val domainState = MutableStateFlow<State>(
        State.EmptyProgress(filters = historyFiltersProvider.currentFilters())
    )

    private val assetPayload = MutableSharedFlow<AssetPayload>()

    override fun setAssetPayload(asset: AssetPayload) {
        launch {
            assetPayload.emit(asset)
        }
    }

    override val state = domainState.map(::mapOperationHistoryStateToUi)
        .inBackground()
        .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

    private val cachedPage = flow<CursorPage<Operation>> {
        walletInteractor.operationsFirstPageFlow(assetPayload.first().chainId, assetPayload.first().chainAssetId)
            .distinctUntilChangedBy { it.cursorPage }
            .onEach { performTransition(Action.CachePageArrived(it.cursorPage, it.accountChanged)) }
            .map { it.cursorPage }
    }.inBackground().shareIn(this, SharingStarted.Eagerly, replay = 1)

    init {
        historyFiltersProvider.filtersFlow()
            .distinctUntilChanged()
            .onEach { performTransition(Action.FiltersChanged(it)) }
            .launchIn(this)
    }

    override fun scrolled(currentIndex: Int) {
        launch { performTransition(Action.Scrolled(currentIndex)) }
    }

    override suspend fun syncFirstOperationsPage(): Result<*> {
        return walletInteractor.syncOperationsFirstPage(
            chainId = assetPayload.first().chainId,
            chainAssetId = assetPayload.first().chainAssetId,
            pageSize = TransactionStateMachine.PAGE_SIZE,
            filters = historyFiltersProvider.allFilters
        ).onFailure { throwable ->
            val message = when (throwable) {
                is HistoryNotSupportedException -> resourceManager.getString(R.string.wallet_transaction_history_unsupported_message)
                else -> resourceManager.getString(R.string.wallet_transaction_history_error_message)
            }
            domainState.emit(State.Empty(domainState.value.filters, message))
        }
    }

    override fun transactionClicked(transactionModel: OperationModel) {
        launch {
            val operations = (domainState.first() as? State.WithData)?.data ?: return@launch

            val clickedOperation = operations.first { it.id == transactionModel.id }

            withContext(Dispatchers.Main) {
                when (val operation = mapOperationToParcel(clickedOperation, resourceManager)) {
                    is OperationParcelizeModel.Transfer -> {
                        router.openTransferDetail(operation, assetPayload.first())
                    }

                    is OperationParcelizeModel.Extrinsic -> {
                        router.openExtrinsicDetail(ExtrinsicDetailsPayload(operation, assetPayload.first().chainId))
                    }

                    is OperationParcelizeModel.Reward -> {
                        router.openRewardDetail(RewardDetailsPayload(operation, assetPayload.first().chainId))
                    }
                }
            }
        }
    }

    private suspend fun performTransition(action: Action) = withContext(Dispatchers.Default) {
        val newState = TransactionStateMachine.transition(action, domainState.value) { sideEffect ->
            when (sideEffect) {
                is TransactionStateMachine.SideEffect.ErrorEvent -> {
                    // ignore errors here, they are bypassed to client of mixin
                }
                is TransactionStateMachine.SideEffect.LoadPage -> loadNewPage(sideEffect)
                TransactionStateMachine.SideEffect.TriggerCache -> triggerCache()
            }
        }

        domainState.value = newState
    }

    private fun triggerCache() {
        val cached = cachedPage.replayCache.firstOrNull()

        cached?.let {
            launch { performTransition(Action.CachePageArrived(it, accountChanged = false)) }
        }
    }

    private fun loadNewPage(sideEffect: TransactionStateMachine.SideEffect.LoadPage) {
        launch {
            walletInteractor.getOperations(
                assetPayload.first().chainId,
                assetPayload.first().chainAssetId,
                sideEffect.pageSize,
                sideEffect.nextCursor,
                sideEffect.filters
            )
                .onFailure {
                    performTransition(Action.PageError(error = it))
                }.onSuccess {
                    performTransition(Action.NewPage(newPage = it, loadedWith = sideEffect.filters))
                }
        }
    }

    private suspend fun mapOperationHistoryStateToUi(state: State): TransactionHistoryUi.State {
        return when (state) {
            is State.Empty -> TransactionHistoryUi.State.Empty(state.message)
            is State.EmptyProgress -> TransactionHistoryUi.State.EmptyProgress
            is State.Data -> TransactionHistoryUi.State.Data(transformData(state.data))
            is State.FullData -> TransactionHistoryUi.State.Data(transformData(state.data))
            is State.NewPageProgress -> TransactionHistoryUi.State.Data(transformData(state.data))
        }
    }

    private suspend fun transformData(data: List<Operation>): List<Any> {
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
