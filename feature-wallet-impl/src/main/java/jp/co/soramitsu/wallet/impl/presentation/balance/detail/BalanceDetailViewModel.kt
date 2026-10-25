package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanRecovery
import jp.co.soramitsu.crowdloan.api.domain.shouldShowLegacyCrowdloan
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedTransactionExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.send.showSendPausedSheet
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.xcm.XcmService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter

@HiltViewModel
class BalanceDetailViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val buyMixin: BuyMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    addressIconGenerator: AddressIconGenerator,
    chainInteractor: ChainInteractor,
    historyFiltersProvider: HistoryFiltersProvider,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    addressDisplayUseCase: AddressDisplayUseCase,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val xcmService: XcmService,
    private val legacyCrowdloanRecovery: LegacyCrowdloanRecovery,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val sendPausePolicy: SendPausePolicy,
    private val nativeRecovery: jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecovery? = null
) : BaseViewModel(),
    BalanceDetailsScreenInterface,
    DefaultLifecycleObserver,
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    companion object {
        private const val LOCKED_BALANCE_INFO_ID = 409
        private const val LEGACY_CROWDLOAN_INFO_ID = 410
    }

    private val assetPayloadInitial: AssetPayload =
        savedStateHandle[KEY_ASSET_PAYLOAD] ?: error("No asset specified")

    val nativeRecoveryUi = MutableStateFlow<jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState?>(null)
    private val nativeRecoveryRefresh = MutableStateFlow(0L)

    val transactionDetailsBottomSheet = MutableStateFlow<TransactionDetailsState?>(null)
    val externalActionsSelector = MutableStateFlow<ExternalAccountActions.Payload?>(null)

    private val _showAccountOptions = MutableLiveData<Event<AccountOptionsPayload>>()
    val showAccountOptions: LiveData<Event<AccountOptionsPayload>> = _showAccountOptions

    private val _shareUrlEvent = MutableLiveData<Event<String>>()
    val shareUrlEvent = _shareUrlEvent

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val selectedChainId = MutableStateFlow(assetPayloadInitial.chainId)
    private val assetPayload = MutableStateFlow(assetPayloadInitial)
    private val legacyCrowdloanRefresh = MutableStateFlow(0L)

    private val chainsFlow = chainInteractor.getChainsFlow().share()
    private val chainsItemStateFlow = chainsFlow.mapList { it.toChainItemState() }
    private val selectedChainFlow = selectedChainId.map { interactor.getChain(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val assetModelFlow = assetPayload
        .flatMapLatest {
            interactor.assetFlow(it.chainId, it.chainAssetId)
                .catch { showError("Failed to load balance of the asset, try to change node or come back later") }
        }
        .distinctUntilChanged()
        .onEach {

            assetPayload.emit(
                AssetPayload(
                    chainId = it.token.configuration.chainId,
                    chainAssetId = it.token.configuration.id
                )
            )
        }
        .share()

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        viewModelScope.launch {
            transactionHistoryProvider.tryReloadHistory()
            nativeRecoveryRefresh.value++
        }
    }

    private suspend fun isBuyEnabled(): Boolean {
        return buyMixin.isBuyEnabled(
            assetPayload.value.chainId,
            assetPayload.value.chainAssetId
        )
    }

    private val transactionHistoryProvider = TransactionHistoryProvider(
        walletInteractor = interactor,
        iconGenerator = addressIconGenerator,
        router = router,
        historyFiltersProvider = historyFiltersProvider,
        resourceManager = resourceManager,
        addressDisplayUseCase = addressDisplayUseCase,
        assetPayloadStateFlow = assetPayload
    )

    val toolbarState = combine(
        selectedChainId,
        chainsItemStateFlow
    ) { chainId, chainItems ->
        val selectedChain = chainItems.firstOrNull {
            it.id == chainId
        }
        LoadingState.Loaded(
            MainToolbarViewState(
                title = interactor.getSelectedMetaAccount().name,
                homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_arrow_back_24dp),
                selectorViewState = ChainSelectorViewState(selectedChain?.title, selectedChain?.id)
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    private val transactionHistory: Flow<TransactionHistoryUi.State> =
        transactionHistoryProvider.state()

    private val defaultState = BalanceDetailsState(
        LoadingState.Loading(),
        LoadingState.Loading(),
        TitleValueViewState(title = resourceManager.getString(R.string.assetdetails_balance_transferable)),
        TitleValueViewState(title = resourceManager.getString(R.string.assetdetails_balance_locked)),
        null,
        TransactionHistoryUi.State.EmptyProgress,
        false
    )

    val state: MutableStateFlow<BalanceDetailsState>  = MutableStateFlow(defaultState)

    init {
        router.chainSelectorPayloadFlow.onEach { chainId ->
            resolveNetworkScopedAssetPayload(assetPayloadInitial, chainId)?.let { exactAsset ->
                assetPayload.value = exactAsset
                selectedChainId.value = exactAsset.chainId
            }
        }.launchIn(viewModelScope)

        selectedChainId.onEach { chainId ->
            BalanceUpdateTrigger.invoke(chainId = chainId)
        }.launchIn(viewModelScope)

        transactionHistoryProvider.sideEffects().onEach {
            when (it) {
                is TransactionHistoryUi.SideEffect.Error -> showError(
                    it.message
                        ?: resourceManager.getString(R.string.common_undefined_error_message)
                )
            }
        }.launchIn(viewModelScope)

        subscribeScreenState()
        subscribeLegacyCrowdloanState()
        subscribeNativeRecovery()
    }

    private fun subscribeNativeRecovery() {
        val recovery = nativeRecovery ?: return
        combine(selectedChainId, interactor.selectedMetaAccountFlow(), nativeRecoveryRefresh) { chainId, wallet, _ -> chainId to wallet.id }
            .onEach { (chainId, walletId) ->
                nativeRecoveryUi.value?.snapshot?.let {
                    if (it.walletId != walletId || it.chainId != chainId ||
                        !jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(it.selectionEpoch)) nativeRecoveryUi.value = null
                }
                val record = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { recovery.peek(chainId, walletId) }
                } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (_: Exception) { null }
                if (selectedChainId.value == chainId && interactor.getSelectedMetaAccount().id == walletId) {
                    state.update { it.copy(nativePaymentAvailable = record != null) }
                }
            }.launchIn(viewModelScope)
    }

    override fun onNativePaymentClick() {
        val recovery = nativeRecovery ?: return
        launch {
            val walletId = interactor.getSelectedMetaAccount().id
            val chainId = selectedChainId.value
            val record = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { recovery.peek(chainId, walletId) }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (_: Exception) {
                showError(resourceManager.getString(R.string.send_recovery_unavailable)); null
            } ?: return@launch
            if (selectedChainId.value != chainId || interactor.getSelectedMetaAccount().id != walletId ||
                !jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(record.selectionEpoch)) return@launch
            nativeRecoveryUi.value = jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState(record)
            if (record.networkAvailable) refreshNativePayment(false)
        }
    }

    fun closeNativePayment() { nativeRecoveryUi.value = null }

    fun refreshNativePayment(retry: Boolean) {
        val recovery = nativeRecovery ?: return
        val before = nativeRecoveryUi.value?.takeUnless { it.busy || !it.snapshot.networkAvailable } ?: return
        nativeRecoveryUi.value = before.copy(busy = true, error = null)
        launch {
            val result = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { recovery.refresh(before.snapshot, retry) }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (_: Exception) { null }
            if (nativeRecoveryUi.value?.snapshot == before.snapshot) {
                if (!jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(before.snapshot.selectionEpoch)) {
                    nativeRecoveryUi.value = null
                } else nativeRecoveryUi.value = before.copy(snapshot = result ?: before.snapshot, busy = false,
                    error = if (result == null) resourceManager.getString(R.string.send_recovery_unavailable) else null)
            }
            nativeRecoveryRefresh.value++
            transactionHistoryProvider.tryReloadHistory()
        }
    }

    fun openNativePaymentExplorer() {
        val snapshot = nativeRecoveryUi.value?.snapshot?.takeIf { it.networkAvailable } ?: return
        launch {
            val chain = try { nativeRecovery?.explorerChain(snapshot) } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) { null } ?: return@launch
            if (nativeRecoveryUi.value?.snapshot != snapshot || selectedChainId.value != snapshot.chainId ||
                !jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(snapshot.selectionEpoch)) return@launch
            jp.co.soramitsu.wallet.impl.presentation.send.NativeSendExplorer.url(chain, snapshot.transactionId, true)?.let(::openUrl)
        }
    }

    override fun onNativeHistoryClick() {
        launch {
            val epoch = jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot()
            val wallet = interactor.getSelectedMetaAccount()
            val chainId = selectedChainId.value
            val address = currentAccountAddress(chainId) ?: return@launch
            val chain = interactor.getChain(chainId)
            if (selectedChainId.value != chainId || interactor.getSelectedMetaAccount().id != wallet.id ||
                !jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(epoch)) return@launch
            jp.co.soramitsu.wallet.impl.presentation.send.NativeSendExplorer.url(chain, address, false)?.let(::openUrl)
        }
    }

    private fun subscribeLegacyCrowdloanState() {
        assetPayload.onEach {
            state.update { previous -> previous.copy(legacyCrowdloanViewState = null) }
        }.launchIn(viewModelScope)

        combine(assetPayload, legacyCrowdloanRefresh) { payload, _ -> payload }.flatMapLatest { payload ->
            flow {
                val chain = interactor.getChain(payload.chainId)
                val utilityAssetId = chain.assets.firstOrNull { it.isUtility }?.id
                val evidence = legacyCrowdloanRecovery.findEvidence(
                    chainId = payload.chainId,
                    chainAssetId = payload.chainAssetId
                )
                val entryState = if (
                    shouldShowLegacyCrowdloan(
                        chainId = payload.chainId,
                        chainAssetId = payload.chainAssetId,
                        utilityAssetId = utilityAssetId,
                        evidence = evidence
                    )
                ) {
                    TitleValueViewState(
                        title = resourceManager.getString(R.string.legacy_crowdloan_title),
                        value = resourceManager.getString(R.string.legacy_crowdloan_review),
                        clickState = TitleValueViewState.ClickState.Value(
                            R.drawable.ic_arrow_right_24,
                            LEGACY_CROWDLOAN_INFO_ID
                        )
                    )
                } else {
                    null
                }

                emit(entryState)
            }.catch { emit(null) }
        }.onEach { entryState ->
            state.update { previous -> previous.copy(legacyCrowdloanViewState = entryState) }
        }.launchIn(viewModelScope)
    }

    private fun subscribeScreenState() {
        transactionHistory.onEach {  historyState ->
            state.update { prevState ->
                prevState.copy(transactionHistory = historyState)
            }
            if (
                historyState is TransactionHistoryUi.State.Data ||
                historyState is TransactionHistoryUi.State.Empty
            ) {
                legacyCrowdloanRefresh.update { it + 1 }
            }
        }.launchIn(viewModelScope)

        assetModelFlow.onEach { balanceModel ->
            val unavailable = resourceManager.getString(R.string.portfolio_balance_unavailable)
            val balanceState = AssetBalanceViewState(
                transferableBalance = balanceModel.transferable?.formatCryptoDetail(balanceModel.token.configuration.symbol) ?: unavailable,
                address = currentAccountAddress(chainId = balanceModel.token.configuration.chainId).orEmpty(),
                isInfoEnabled = false,
                changeViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.token.recentRateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.transferable?.let(balanceModel.token::fiatAmount)
                        ?.formatFiat(balanceModel.token.fiatSymbol).orEmpty()
                )
            )

            val selectedChainId = balanceModel.token.configuration.chainId

            val actionBarState = LoadingState.Loaded(
                ActionBarViewState(
                    chainId = selectedChainId,
                    chainAssetId = balanceModel.token.configuration.id,
                    actionItems = getActionItems(selectedChainId, balanceModel),
                    disabledItems = getDisabledItems()
                )
            )

            val transferableFormatted =
                balanceModel.sendAvailable?.formatCryptoDetail(balanceModel.token.configuration.symbol) ?: unavailable
            val transferableFiat = balanceModel.sendAvailable?.let(balanceModel.token::fiatAmount)
                ?.formatFiat(balanceModel.token.fiatSymbol)
            val newTransferableState = defaultState.transferableViewState.copy(
                value = transferableFormatted,
                additionalValue = transferableFiat
            )

            val showLocked = if (balanceModel.isAssetFrozen) {
                balanceModel.transferable
            } else {
                balanceModel.locked
            }
            val lockedFormatted = showLocked?.formatCryptoDetail(balanceModel.token.configuration.symbol) ?: unavailable
            val lockedFiat = showLocked?.let(balanceModel.token::fiatAmount)?.formatFiat(balanceModel.token.fiatSymbol)

            val bitcoinPending = selectedChainFlow.first().isUniversalWalletBitcoin()
            val newLockedState = defaultState.lockedViewState.copy(
                title = resourceManager.getString(if (bitcoinPending) R.string.transaction_status_pending else R.string.assetdetails_balance_locked),
                value = lockedFormatted,
                additionalValue = lockedFiat,
                clickState = if (!bitcoinPending && balanceModel.locked?.signum() == 1) {
                    TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, LOCKED_BALANCE_INFO_ID)
                } else {
                    null
                }
            )
            val chain = selectedChainFlow.first()
            val filtersEnabled = chain.isEthereumChain || chain.ecosystem == Ecosystem.Ton
            state.update { prevState ->
                prevState.copy(
                    actionBarViewState = actionBarState,
                    balance = LoadingState.Loaded(balanceState),
                    transferableViewState = newTransferableState,
                    lockedViewState = newLockedState,
                    filtersEnabled = filtersEnabled,
                    nativeHistoryExplorer = chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()

        transactionHistoryProvider.cancel()
    }

    override fun transactionsScrolled(index: Int) {
        transactionHistoryProvider.scrolled(
            index,
            AssetPayload(
                chainId = assetPayload.value.chainId,
                chainAssetId = assetPayload.value.chainAssetId
            )
        )
    }

    override fun filterClicked() {
        viewModelScope.launch {
            val chain = interactor.getChain(assetPayload.value.chainId)
            if (chain.ecosystem == Ecosystem.Ton) {
                val filterValues = setOf(TransactionFilter.TRANSFER, TransactionFilter.EXTRINSIC)
                router.openFilter(filterValues)
            } else {
                router.openFilter()
            }
        }
    }

    override fun sync() {
        viewModelScope.launch {
            val deferredAssetSync = async { interactor.syncAssetsRates() }
            deferredAssetSync.await().exceptionOrNull()?.message?.let(::showMessage)
        }
    }

    override fun onRefresh() {
        viewModelScope.launch {
            val chainId = selectedChainId.value
            BalanceUpdateTrigger.invoke(chainId)

            sync()
            transactionHistoryProvider.tryReloadHistory()
            nativeRecoveryRefresh.value++
        }
    }

    fun backClicked() {
        router.back()
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun openSwapTokensScreen(assetPayload: AssetPayload) {
        router.openSwapTokensScreen(assetPayload.chainId, assetPayload.chainAssetId, null)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    fun accountOptionsClicked() = launch {
        interactor.getChainAddressForSelectedMetaAccount(
            assetPayload.value.chainId
        )?.let { address ->
            val isClaimSupported: Boolean = interactor.checkClaimSupport(assetPayload.value.chainId)
            val isEthereum = interactor
                .getChain(assetPayload.value.chainId)
                .isEthereumChain
            val payload = AccountOptionsPayload(address, isClaimSupported, isEthereum)
            _showAccountOptions.postValue(Event(payload))
        }
    }

    private suspend fun getActionItems(
        selectedChainId: String,
        asset: Asset
    ): List<ActionItemType> {
        val actionItems = mutableListOf(
            ActionItemType.SEND,
            ActionItemType.RECEIVE
        )
        // A reviewed route keeps the action visible while submission is paused; the tap explains why.
        val hasReviewedXcmRoute = runCatching { xcmService.hasReviewedRoute(
            originChainId = selectedChainId,
            originAssetId = asset.token.configuration.id,
            assetSymbol = asset.token.configuration.symbol
        ) }.getOrNull() ?: false

        if (hasReviewedXcmRoute) {
            actionItems += ActionItemType.CROSS_CHAIN
        }
        if (isBuyEnabled()) {
            actionItems += ActionItemType.BUY
        }
        if (selectedChainId in listOf(soraMainChainId, soraTestChainId)) {
            actionItems += ActionItemType.SWAP
        }
        return actionItems
    }

    private suspend fun getDisabledItems(): List<ActionItemType> {
        return if (!isBuyEnabled()) {
            listOf(ActionItemType.BUY)
        } else {
            emptyList()
        }
    }

    private fun buyClicked(assetPayload: AssetPayload) {
        viewModelScope.launch {
            interactor.selectedAccountFlow(assetPayload.chainId).firstOrNull()?.let { wallet ->
                buyMixin.buyClicked(assetPayload.chainId, assetPayload.chainAssetId, wallet.address)
            }
        }
    }

    override fun onAddressClick() {
        (state.value.balance as? LoadingState.Loaded)?.data?.address?.let { address ->
            copyToClipboard(address)
        }
    }

    override fun actionItemClicked(
        actionType: ActionItemType,
        chainId: ChainId,
        chainAssetId: String
    ) {
        val payload = AssetPayload(chainId, chainAssetId)
        when (actionType) {
            ActionItemType.SEND -> {
                onSendClicked(payload)
            }

            ActionItemType.RECEIVE -> {
                receiveClicked(payload)
            }

            ActionItemType.CROSS_CHAIN -> {
                onCrossChainClicked(payload)
            }

            ActionItemType.BUY -> {
                buyClicked(payload)
            }

            ActionItemType.SWAP -> {
                onSwapClicked(payload)
            }

            ActionItemType.HIDE, ActionItemType.SHOW -> {
            }
        }
    }

    private fun onSendClicked(assetPayload: AssetPayload) {
        viewModelScope.launch {
            // The transfer service enforces the veto again, so a failed lookup may proceed.
            val chain = runCatching { interactor.getChain(assetPayload.chainId) }.getOrNull()
            if (chain != null && sendPausePolicy.isPaused(chain)) {
                showSendPausedSheet(
                    resourceManager = resourceManager,
                    error = sendPausePolicy.pausedError(chain),
                    onReceive = { receiveClicked(assetPayload) }
                )
            } else {
                sendClicked(assetPayload)
            }
        }
    }

    private fun onCrossChainClicked(assetPayload: AssetPayload) {
        if (xcmService.canSubmitNow) {
            router.openCrossChainSend(assetPayload)
            return
        }
        viewModelScope.launch {
            val chainName = runCatching { interactor.getChain(assetPayload.chainId).name }.getOrNull()
            showError(
                title = resourceManager.getString(R.string.paused_cross_chain_title),
                message = resourceManager.getString(R.string.paused_cross_chain_message),
                positiveButtonText = chainName
                    ?.let { resourceManager.getString(R.string.paused_cross_chain_send_on_network, it) }
                    ?: resourceManager.getString(R.string.common_action_send),
                negativeButtonText = resourceManager.getString(R.string.common_close),
                positiveClick = { onSendClicked(assetPayload) }
            )
        }
    }

    private fun onSwapClicked(assetPayload: AssetPayload) {
        if (featureToggleStore.polkaswapMutationsEnabled) {
            openSwapTokensScreen(assetPayload)
        } else {
            showError(
                title = resourceManager.getString(R.string.paused_swap_title),
                message = resourceManager.getString(R.string.paused_swap_message),
                positiveButtonText = resourceManager.getString(R.string.common_ok)
            )
        }
    }

    private fun copyToClipboard(text: String) {
        clipboardManager.addToClipboard(text)

        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    fun switchNode() {
        router.openNodes(assetPayload.value.chainId)
    }

    fun claimRewardClicked() {
        router.openClaimRewards(assetPayload.value.chainId)
    }

    fun exportClicked() {
        viewModelScope.launch {
            val sources = interactor.getExportSourceTypes(assetPayload.value.chainId)
            _showExportSourceChooser.value = Event(
                ExportSourceChooserPayload(
                    assetPayload.value.chainId,
                    sources
                )
            )
        }
    }

    fun exportTypeSelected(selected: ExportSource, chainId: ChainId) {
        launch {
            val metaId = interactor.getSelectedMetaAccount().id
            val destination = when (selected) {
                is ExportSource.Json -> router.openExportJsonPassword(metaId, chainId)
                is ExportSource.Seed -> router.openExportSeed(metaId, chainId)
                is ExportSource.Mnemonic -> router.openExportMnemonic(metaId, chainId)
                else -> return@launch
            }

            router.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
        }
    }

    override fun transactionClicked(transactionModel: OperationModel) {
        launch {
            val detailsState = transactionHistoryProvider.getTransactionDetailsState(
                transactionModel = transactionModel,
                assetPayload = assetPayload.value
            )
            transactionDetailsBottomSheet.value = detailsState
        }
    }

    override fun tableItemClicked(itemId: Int) {
        when (itemId) {
            LOCKED_BALANCE_INFO_ID -> openBalanceDetails()
            LEGACY_CROWDLOAN_INFO_ID -> router.openLegacyCrowdloan(assetPayload.value)
        }
    }

    private fun openBalanceDetails() {
        launch {
            assetModelFlow.firstOrNull()?.let { assetModel ->
                router.openFrozenTokens(
                    FrozenAssetPayload(
                        assetSymbol = assetModel.token.configuration.symbol,
                        locked = assetModel.locked,
                        reserved = assetModel.reserved,
                        redeemable = assetModel.redeemable
                    )
                )
            }
        }
    }

    fun onDetailsClose() {
        transactionDetailsBottomSheet.value = null
    }

    fun transactionHashClicked(detailsBottomSheetState: TransactionDetailsState) = viewModelScope.launch {
        if(detailsBottomSheetState is TransferDetailsState) {
            val hash = detailsBottomSheetState.hash.text
            val supportedAddressExplorers = selectedChainFlow.first().explorers.getSupportedTransactionExplorers(hash)
            externalActionsSelector.value = ExternalAccountActions.Payload(hash, supportedAddressExplorers)
        }
    }

    fun fromClicked(detailsBottomSheetState: TransactionDetailsState) {
        require(detailsBottomSheetState is TransferDetailsState)
        viewModelScope.launch {
            detailsBottomSheetState.firstAddress?.input?.let {
                val supportedAddressExplorers = selectedChainFlow.first().explorers.getSupportedAddressExplorers(it)
                externalActionsSelector.value = ExternalAccountActions.Payload(it, supportedAddressExplorers)
            }
        }
    }

    fun toClicked(detailsBottomSheetState: TransactionDetailsState) {
        require(detailsBottomSheetState is TransferDetailsState)
        viewModelScope.launch {
            detailsBottomSheetState.secondAddress?.input?.let {
                externalActionsSelector.value = ExternalAccountActions.Payload(it, selectedChainFlow.first().explorers.getSupportedAddressExplorers(it))
            }
        }
    }

    fun onSwapDetailsHashClick(hash: String) {
        copyStringClicked(hash)
    }

    fun onSwapDetailsSubscanClicked(hash: String) = viewModelScope.launch {
        selectedChainFlow.first().explorers.firstOrNull { it.type == Chain.Explorer.Type.SUBSCAN }?.let {
            BlockExplorerUrlBuilder(it.url, it.types).build(BlockExplorerUrlBuilder.Type.EXTRINSIC, hash)
        }?.let {
            openUrl(it)
        }
    }

    fun onShareSwapClicked(hash: String) = viewModelScope.launch {
        selectedChainFlow.first().explorers.firstOrNull { it.type == Chain.Explorer.Type.SUBSCAN }?.let {
            BlockExplorerUrlBuilder(it.url, it.types).build(BlockExplorerUrlBuilder.Type.EXTRINSIC, hash)
        }?.let {
            shareUrlEvent.value = Event(it)
        }
    }

    fun copyStringClicked(value: String) {
        copyToClipboard(value)
    }

    fun openUrl(url: String) {
        externalAccountActions.viewExternalClicked(url)
    }
}

/**
 * A Portfolio asset detail is scoped to one exact `(chainId, assetId)` pair. Chain-selector
 * results are process-wide, so a result produced by another screen must never remap this detail
 * to a similarly named or similarly symbolized asset on another network.
 */
internal fun resolveNetworkScopedAssetPayload(
    initial: AssetPayload,
    selectedChainId: ChainId?
): AssetPayload? = initial.takeIf { selectedChainId == initial.chainId }
