package jp.co.soramitsu.wallet.impl.presentation.balance.detail

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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedTransactionExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    private val xcmService: XcmService
) : BaseViewModel(),
    BalanceDetailsScreenInterface,
    DefaultLifecycleObserver,
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    companion object {
        private const val LOCKED_BALANCE_INFO_ID = 409
    }

    private val assetPayloadInitial: AssetPayload =
        savedStateHandle[KEY_ASSET_PAYLOAD] ?: error("No asset specified")

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

    private val chainsFlow = chainInteractor.getChainsFlow().share()
    private val chainsItemStateFlow = chainsFlow.mapList { it.toChainItemState() }
    private val selectedChainFlow = selectedChainId.map { interactor.getChain(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val assetModelFlow = selectedChainId.mapNotNull { selectedChainId ->
        val chains = chainsFlow.firstOrNull()
        val selectedChain = chains?.firstOrNull { it.id == selectedChainId }
        val initialSelectedChain = chains?.firstOrNull { it.id == assetPayloadInitial.chainId }

        val initialSelectedAssetSymbol =
            initialSelectedChain?.assets?.firstOrNull { it.id == assetPayloadInitial.chainAssetId }?.symbol
        val newSelectedAsset =
            selectedChain?.assets?.firstOrNull { it.symbol == initialSelectedAssetSymbol }

        newSelectedAsset?.id?.let {
            AssetPayload(
                chainId = selectedChainId,
                chainAssetId = it
            )
        }
    }
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
        TransactionHistoryUi.State.EmptyProgress,
        false
    )

    val state: MutableStateFlow<BalanceDetailsState>  = MutableStateFlow(defaultState)

    init {
        router.chainSelectorPayloadFlow.onEach { chainId ->
            chainId?.let { selectedChainId.value = chainId }
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
    }

    private fun subscribeScreenState() {
        transactionHistory.onEach {  historyState ->
            state.update { prevState ->
                prevState.copy(transactionHistory = historyState)
            }
        }.launchIn(viewModelScope)

        assetModelFlow.onEach { balanceModel ->
            val balanceState = AssetBalanceViewState(
                transferableBalance = balanceModel.transferable.orZero()
                    .formatCryptoDetail(balanceModel.token.configuration.symbol),
                address = currentAccountAddress(chainId = balanceModel.token.configuration.chainId).orEmpty(),
                isInfoEnabled = false,
                changeViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.token.recentRateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.token.fiatRate?.multiply(balanceModel.transferable.orZero())
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
                balanceModel.sendAvailable.formatCryptoDetail(balanceModel.token.configuration.symbol)
            val transferableFiat = balanceModel.token.fiatAmount(balanceModel.sendAvailable)
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
            val lockedFormatted = showLocked.formatCryptoDetail(balanceModel.token.configuration.symbol)
            val lockedFiat = balanceModel.token.fiatAmount(showLocked)?.formatFiat(balanceModel.token.fiatSymbol)

            val newLockedState = defaultState.lockedViewState.copy(
                value = lockedFormatted,
                additionalValue = lockedFiat,
                clickState = if (balanceModel.locked > BigDecimal.ZERO) {
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
                    filtersEnabled = filtersEnabled
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

    fun openSelectChain() {
        launch {
            val asset = assetModelFlow.first()
            router.openSelectChain(asset.token.configuration.id, asset.token.configuration.chainId)
        }
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
        val isXcmSupportAsset = runCatching { xcmService.isXcmSupportAsset(
            originChainId = selectedChainId,
            assetSymbol = asset.token.configuration.symbol
        ) }.getOrNull() ?: false

        if (isXcmSupportAsset) {
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
                sendClicked(payload)
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
                openSwapTokensScreen(payload)
            }

            ActionItemType.HIDE, ActionItemType.SHOW -> {
            }
        }
    }

    private fun onCrossChainClicked(assetPayload: AssetPayload) {
        router.openCrossChainSend(assetPayload)
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
        if (itemId == LOCKED_BALANCE_INFO_ID) {
            openBalanceDetails()
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
