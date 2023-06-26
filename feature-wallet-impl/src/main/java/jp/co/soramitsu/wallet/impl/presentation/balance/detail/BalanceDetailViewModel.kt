package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.common.BuildConfig
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.xcm.XcmService
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    companion object {
        private const val LOCKED_BALANCE_INFO_ID = 409
    }

    private val assetPayloadInitial: AssetPayload = savedStateHandle[KEY_ASSET_PAYLOAD] ?: error("No asset specified")

    private val _showAccountOptions = MutableLiveData<Event<String>>()
    val showAccountOptions: LiveData<Event<String>> = _showAccountOptions

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    private val selectedChainId = MutableStateFlow(assetPayloadInitial.chainId)
    private val assetPayload = MutableStateFlow(assetPayloadInitial)

    private val chainsFlow = chainInteractor.getChainsFlow().mapList { it.toChainItemState() }
    private val assetModelsFlow: Flow<List<AssetModel>> = interactor.assetsFlow()
        .mapList {
            when {
                it.hasAccount -> it.asset
                else -> null
            }
        }
        .map { it.filterNotNull() }
        .mapList { mapAssetToAssetModel(it) }

    private val assetModelFlow = combine(
        assetModelsFlow,
        selectedChainId
    ) { assetModels: List<AssetModel>,
        selectedChainId: ChainId? ->
        val assetSymbol = assetModels.first {
            it.token.configuration.id == assetPayloadInitial.chainAssetId
        }.token.configuration.symbol

        val chainId = selectedChainId ?: assetPayload.value.chainId

        val asset = assetModels.first {
            it.token.configuration.symbol == assetSymbol && it.token.configuration.chainId == chainId
        }

        assetPayload.emit(
            AssetPayload(chainId = chainId, chainAssetId = asset.token.configuration.id)
        )

        return@combine interactor.getCurrentAsset(chainId, asset.token.configuration.id)
    }.share()

    private fun isBuyEnabled(): Boolean {
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
        chainsFlow
    ) { chainId, chainItems ->
        val selectedChain = chainItems.first {
            it.id == chainId
        }
        LoadingState.Loaded(
            MainToolbarViewState(
                title = interactor.getSelectedMetaAccount().name,
                homeIconState = ToolbarHomeIconState(navigationIcon = R.drawable.ic_arrow_back_24dp),
                selectorViewState = ChainSelectorViewState(selectedChain.title, selectedChain.id)
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
        TransactionHistoryUi.State.EmptyProgress
    )

    val state = combine(
        transactionHistory,
        assetModelFlow
    ) { transactionHistory: TransactionHistoryUi.State,
        balanceModel: Asset ->

        val balanceState = AssetBalanceViewState(
            transferableBalance = balanceModel.transferable.orZero().formatCryptoDetail(balanceModel.token.configuration.symbol),
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

        val transferableFormatted = balanceModel.transferable.formatCryptoDetail(balanceModel.token.configuration.symbol)
        val transferableFiat = balanceModel.token.fiatAmount(balanceModel.transferable)?.formatFiat(balanceModel.token.fiatSymbol)
        val newTransferableState = defaultState.transferableViewState.copy(value = transferableFormatted, additionalValue = transferableFiat)

        val lockedFormatted = balanceModel.locked.formatCryptoDetail(balanceModel.token.configuration.symbol)
        val lockedFiat = balanceModel.token.fiatAmount(balanceModel.locked)?.formatFiat(balanceModel.token.fiatSymbol)
        val newLockedState = defaultState.lockedViewState.copy(
            value = lockedFormatted,
            additionalValue = lockedFiat,
            clickState = if (balanceModel.locked > BigDecimal.ZERO) {
                TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, LOCKED_BALANCE_INFO_ID)
            } else {
                null
            }
        )

        BalanceDetailsState(
            actionBarViewState = actionBarState,
            balance = LoadingState.Loaded(balanceState),
            transferableViewState = newTransferableState,
            lockedViewState = newLockedState,
            transactionHistory = transactionHistory
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = defaultState)

    init {
        viewModelScope.launch {
            router.chainSelectorPayloadFlow.collect { chainId ->
                chainId?.let { selectedChainId.value = chainId }
            }
            transactionHistoryProvider.sideEffects().collect {
                when (it) {
                    is TransactionHistoryUi.SideEffect.Error -> showError(it.message ?: resourceManager.getString(R.string.common_undefined_error_message))
                }
            }
        }
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
        router.openFilter()
    }

    override fun sync() {
        viewModelScope.launch {
            transactionHistoryProvider.syncFirstOperationsPage(
                AssetPayload(
                    chainId = assetPayload.value.chainId,
                    chainAssetId = assetPayload.value.chainAssetId
                )
            )

            val deferredAssetSync = async { interactor.syncAssetsRates() }
            deferredAssetSync.await().exceptionOrNull()?.message?.let(::showMessage)
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
            _showAccountOptions.postValue(Event(address))
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
        val isXcmSupportAsset = xcmService.isXcmSupportAsset(
            originChainId = selectedChainId,
            assetSymbol = asset.token.configuration.symbol
        )

        if (BuildConfig.DEBUG && isXcmSupportAsset) {
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

    private fun getDisabledItems(): List<ActionItemType> {
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

    override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {
        val payload = AssetPayload(chainId, chainAssetId)
        when (actionType) {
            ActionItemType.SEND -> {
                sendClicked(payload)
            }
            ActionItemType.RECEIVE -> {
                receiveClicked(payload)
            }
            ActionItemType.TELEPORT -> {
                showMessage("YOU NEED THE BLUE KEY")
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

    fun exportClicked() {
        viewModelScope.launch {
            val isEthereumBased = interactor
                .getChain(assetPayload.value.chainId)
                .isEthereumBased
            val sources = interactor.getMetaAccountSecrets().buildExportSourceTypes(isEthereumBased)
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
            }

            router.withPinCodeCheckRequired(destination, pinCodeTitleRes = R.string.account_export)
        }
    }

    override fun transactionClicked(transactionModel: OperationModel) {
        transactionHistoryProvider.transactionClicked(
            transactionModel,
            AssetPayload(
                chainId = assetPayload.value.chainId,
                chainAssetId = assetPayload.value.chainAssetId
            )
        )
    }

    override fun tableItemClicked(itemId: Int) {
        if (itemId == LOCKED_BALANCE_INFO_ID) {
            openBalanceDetails()
        }
    }

    private fun openBalanceDetails() {
        launch {
            val assetModel = assetModelFlow.first()
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
