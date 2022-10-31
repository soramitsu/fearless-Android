package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.api.presentation.exporting.buildExportSourceTypes
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryProvider
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class BalanceDetailViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val buyMixin: BuyMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val addressIconGenerator: AddressIconGenerator,
    private val chainInteractor: ChainInteractor,
    private val historyFiltersProvider: HistoryFiltersProvider,
    savedStateHandle: SavedStateHandle,
    resourceManager: ResourceManager,
    addressDisplayUseCase: AddressDisplayUseCase
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    BuyMixin by buyMixin {

    private val assetPayloadInitial: AssetPayload = savedStateHandle[KEY_ASSET_PAYLOAD]!!

    private val accountAddressToChainItemMap = mutableMapOf<String, ChainItemState?>(polkadotChainId to null)

    private val _showAccountOptions = MutableLiveData<Event<String>>()
    val showAccountOptions: LiveData<Event<String>> = _showAccountOptions

    private val _showExportSourceChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    val showExportSourceChooser: LiveData<Event<ExportSourceChooserPayload>> = _showExportSourceChooser

    val isRefreshing = MutableStateFlow(false)

    private val enteredChainQueryFlow = MutableStateFlow("")

    private val selectedChainItem = MutableStateFlow<ChainItemState?>(null)
    private val assetPayload = MutableStateFlow(assetPayloadInitial)

    private val chainsFlow = chainInteractor.getChainsFlow().mapList { it.toChainItemState() }

    private val assetModelFlow = combine(
        assetModelsFlow(),
        chainsFlow,
        selectedChainItem
    ) { assetModels: List<AssetModel>,
        chains: List<ChainItemState>,
        selectedChain: ChainItemState? ->
        val assetSymbol = assetModels.first {
            it.token.configuration.id == assetPayloadInitial.chainAssetId
        }.token.configuration.symbol

        val chain = selectedChain ?: chains.first { it.id == assetPayload.value.chainId }
        val asset = assetModels.first { asset ->
            asset.token.configuration.symbol == assetSymbol && asset.token.configuration.chainId == chain.id
        }

        assetPayload.emit(
            AssetPayload(
                chainId = chain.id,
                chainAssetId = asset.token.configuration.id
            )
        )

        return@combine interactor.getCurrentAsset(
            chain.id,
            asset.token.configuration.id
        )
    }.shareIn(this, SharingStarted.Eagerly, replay = 1)

    fun buyEnabled(): Boolean {
        return buyMixin.isBuyEnabled(
            assetPayload.value.chainId,
            assetPayload.value.chainAssetId
        )
    }

    val chainsState = combine(
        chainsFlow,
        selectedChainItem,
        enteredChainQueryFlow,
        assetModelsFlow()
    ) { chainItems, selectedChain, searchQuery, assetModels: List<AssetModel> ->
        val assetSymbol = assetModels.first {
            it.token.configuration.id == assetPayloadInitial.chainAssetId
        }.token.configuration.symbol

        val chains = chainItems
            .filter {
                it.tokenSymbols.any { it.second.contains(assetSymbol) }
            }
            .filter {
                searchQuery.isEmpty() || it.title.contains(searchQuery, true) || it.tokenSymbols.any { it.second.contains(searchQuery, true) }
            }
            .sortedWith(compareBy<ChainItemState> { it.id.defaultChainSort() }.thenBy { it.title })

        ChainSelectScreenViewState(
            chains = chains,
            selectedChainId = assetPayload.value.chainId,
            searchQuery = searchQuery
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChainSelectScreenViewState.default)

    private val transactionHistoryMixin = TransactionHistoryProvider(
        interactor,
        addressIconGenerator,
        router,
        historyFiltersProvider,
        resourceManager,
        addressDisplayUseCase,
        assetPayloadFlow = flow { assetPayload.value },
        assetPayloadStateFlow = assetPayload
    )

    val toolbarState = combine(
        currentAddressModelFlow(),
        selectedChainItem,
        chainsFlow
    ) { addressModel, chain, chainItems ->
        val selectedChain = chainItems.first {
            it.id == (chain?.id ?: assetPayload.value.chainId)
        }
        LoadingState.Loaded(
            MainToolbarViewState(
                title = addressModel.nameOrAddress,
                homeIconState = ToolbarHomeIconState(navigationIcon = R.drawable.ic_arrow_back_24dp),
                selectorViewState = ChainSelectorViewState(selectedChain.title, selectedChain.id)
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    private val transactionHistory: Flow<TransactionHistoryUi.State> =
        transactionHistoryMixin.state()

    val state = combine(
        transactionHistory,
        assetModelFlow,
        enteredChainQueryFlow,
        assetModelsFlow()
    ) { transactionHistory: TransactionHistoryUi.State,
        balanceModel: Asset,
        searchQuery,
        assetModels: List<AssetModel> ->

        if (transactionHistory is TransactionHistoryUi.State.EmptyProgress) {
            return@combine LoadingState.Loading()
        }

        val balanceState = AssetBalanceViewState(
            balance = balanceModel.total.orZero().formatTokenAmount(balanceModel.token.configuration.symbolToShow.uppercase()),
            address = "",
            isInfoEnabled = true,
            changeViewState = ChangeBalanceViewState(
                percentChange = balanceModel.token.recentRateChange?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.token.fiatRate?.multiply(balanceModel.total.orZero())?.formatAsCurrency(balanceModel.token.fiatSymbol).orEmpty()
            )
        )

        LoadingState.Loaded(
            BalanceDetailsState(
                balance = balanceState,
                transactionHistory = transactionHistory,
                selectedChainId = balanceModel.token.configuration.chainId,
                chainAssetId = balanceModel.token.configuration.id
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    override fun onCleared() {
        super.onCleared()

        transactionHistoryMixin.cancel()
    }

    fun transactionsScrolled(index: Int) {
        transactionHistoryMixin.scrolled(
            index,
            AssetPayload(
                chainId = assetPayload.value.chainId,
                chainAssetId = assetPayload.value.chainAssetId
            )
        )
    }

    fun filterClicked() {
        router.openFilter()
    }

    fun sync() {
        viewModelScope.launch {
            isRefreshing.value = true
            async {
                transactionHistoryMixin.syncFirstOperationsPage(
                    AssetPayload(
                        chainId = assetPayload.value.chainId,
                        chainAssetId = assetPayload.value.chainAssetId
                    )
                )
            }.start()

            val deferredAssetSync = async { interactor.syncAssetsRates() }
            deferredAssetSync.await().exceptionOrNull()?.message?.let(::showMessage)

            isRefreshing.value = false
        }
    }

    fun backClicked() {
        router.back()
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    fun accountOptionsClicked() = launch {
        interactor.getChainAddressForSelectedMetaAccount(
            assetPayload.value.chainId
        )?.let { address ->
            _showAccountOptions.postValue(Event(address))
        }
    }

    private fun buyClicked(assetPayload: AssetPayload) {
        viewModelScope.launch {
            interactor.selectedAccountFlow(assetPayload.chainId).firstOrNull()?.let { wallet ->
                buyMixin.buyClicked(assetPayload.chainId, assetPayload.chainAssetId, wallet.address)
            }
        }
    }

    fun frozenInfoClicked() {
        launch {
            val assetModel = assetModelFlow.first()
            router.openFrozenTokens(
                FrozenAssetPayload(
                    assetSymbol = assetModel.token.configuration.symbolToShow,
                    locked = assetModel.locked,
                    reserved = assetModel.reserved,
                    redeemable = assetModel.redeemable
                )
            )
        }
    }

    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {
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
            ActionItemType.BUY -> {
                buyClicked(payload)
            }
            else -> {}
        }
    }

    private fun assetModelsFlow(): Flow<List<AssetModel>> =
        interactor.assetsFlow()
            .mapList {
                when {
                    it.hasAccount -> it.asset
                    else -> null
                }
            }
            .map { it.filterNotNull() }
            .mapList { mapAssetToAssetModel(it) }

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

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow(polkadotChainId)
            .onEach {
                if (accountAddressToChainItemMap.containsKey(it.address).not()) {
                    selectedChainItem.value = null
                    accountAddressToChainItemMap[it.address] = null
                } else {
                    selectedChainItem.value = accountAddressToChainItemMap.getOrDefault(it.address, null)
                }
            }
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    fun onChainSelected(item: ChainItemState? = null) {
        selectedChainItem.value = item
        viewModelScope.launch {
            val currentAddress = interactor.selectedAccountFlow(polkadotChainId).first().address
            accountAddressToChainItemMap[currentAddress] = item
        }
    }

    fun onChainSearchEntered(query: String) {
        enteredChainQueryFlow.value = query
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    fun transactionClicked(transactionModel: OperationModel) {
        transactionHistoryMixin.transactionClicked(
            transactionModel,
            AssetPayload(
                chainId = assetPayload.value.chainId,
                chainAssetId = assetPayload.value.chainAssetId
            )
        )
    }

    private fun calculateTotalBalance(
        freeInPlanks: BigInteger?,
        reservedInPlanks: BigInteger?
    ) = freeInPlanks?.let { freeInPlanks + reservedInPlanks.orZero() }
}
