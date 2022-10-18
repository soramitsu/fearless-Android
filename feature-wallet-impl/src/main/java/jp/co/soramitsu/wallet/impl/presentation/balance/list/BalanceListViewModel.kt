package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.domain.FiatCurrencies
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.get
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.SelectChainScreenViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceModel
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.AssetUpdateState
import jp.co.soramitsu.wallet.impl.presentation.model.AssetWithStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class BalanceListViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val accountRepository: AccountRepository,
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin {

    private val accountAddressToChainItemMap = mutableMapOf<String, ChainItemState?>(polkadotChainId to null)

    private val _hideRefreshEvent = MutableLiveData<Event<Unit>>()
    val hideRefreshEvent: LiveData<Event<Unit>> = _hideRefreshEvent

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _decodeAddressResult = MutableLiveData<Event<String>>()
    val decodeAddressResult: LiveData<Event<String>> = _decodeAddressResult

    private val enteredChainQueryFlow = MutableStateFlow("")

    private val connectingChainIdsFlow = networkStateMixin.chainConnectionsLiveData.map {
        it.filter { (_, isConnecting) -> isConnecting }.keys
    }.asFlow()

    private val fiatSymbolFlow = combine(selectedFiat.flow(), getAvailableFiatCurrencies.flow()) { selectedFiat: String, fiatCurrencies: FiatCurrencies ->
        fiatCurrencies[selectedFiat]?.symbol
    }.onEach {
        sync()
    }

    private val chainsFlow = chainInteractor.getChainsFlow().mapList {
        it.toChainItemState()
    }
    private val selectedChainItem = MutableStateFlow<ChainItemState?>(null)

    val chainsState = combine(chainsFlow, selectedChainItem, enteredChainQueryFlow) { chainItems, selectedChain, searchQuery ->
        val chains = chainItems
            .filter {
                searchQuery.isEmpty() || it.title.contains(searchQuery, true) || it.tokenSymbols.any { it.contains(searchQuery, true) }
            }
            .sortedWith(compareBy<ChainItemState> { it.id.defaultChainSort() }.thenBy { it.title })

        SelectChainScreenViewState(
            chains = chains,
            selectedChain = selectedChain,
            searchQuery = searchQuery
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectChainScreenViewState(emptyList(), null, null))

    private val fiatSymbolLiveData = fiatSymbolFlow.asLiveData()
    private val assetModelsLiveData = assetModelsFlow().asLiveData()

    val balanceLiveData = mediateWith(
        assetModelsLiveData,
        fiatSymbolLiveData,
        tokenRatesUpdate,
        assetsUpdate,
        chainsUpdate
    ) { (assetModels: List<AssetModel>?, fiatSymbol: String?, tokenRatesUpdate: Set<String>?, assetsUpdate: Set<AssetKey>?, chainsUpdate: Set<String>?) ->
        val assetsWithState = assetModels?.map { asset ->
            val rateUpdate = tokenRatesUpdate?.let { asset.token.configuration.id in it }
            val balanceUpdate = assetsUpdate?.let { asset.primaryKey in it }
            val chainUpdate = chainsUpdate?.let { asset.token.configuration.chainId in it }
            val isTokenFiatChanged = when {
                fiatSymbol == null -> false
                asset.token.fiatSymbol == null -> false
                else -> fiatSymbol != asset.token.fiatSymbol
            }

            AssetWithStateModel(
                asset = asset,
                state = AssetUpdateState(rateUpdate, balanceUpdate, chainUpdate, isTokenFiatChanged)
            )
        }.orEmpty()

        BalanceModel(assetsWithState, fiatSymbol.orEmpty())
    }

    private val hiddenAssetsState = MutableLiveData(HiddenItemState(isExpanded = false))

    private val assetTypeSelectorState = MutableLiveData(
        MultiToggleButtonState(
            currentSelection = AssetType.Currencies,
            toggleStates = AssetType.values().toList()
        )
    )

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow(),
        selectedChainItem,
        connectingChainIdsFlow
    ) { assets: List<AssetWithStatus>, chains: List<JoinedChainInfo>, selectedChain: ChainItemState?, chainConnecting: Set<ChainId> ->
        val assetStates = mutableListOf<AssetListItemViewState>()
        assets
            .filter { it.hasAccount || !it.asset.markedNotNeed }
            .filter { selectedChain?.id == null || selectedChain.id == it.asset.token.configuration.chainId }
            .sortedWith(defaultAssetListSort())
            .map { assetWithStatus ->
                val token = assetWithStatus.asset.token
                val chainAsset = token.configuration

                val chainLocal = chains.find { it.chain.id == token.configuration.chainId }
                val chain = chainLocal?.let { mapChainLocalToChain(it) }

                val isSupported: Boolean = when (chain?.minSupportedVersion) {
                    null -> true
                    else -> AppVersion.isSupported(chain.minSupportedVersion)
                }

                val hasNetworkIssue = token.configuration.chainId in chainConnecting

                val assetChainUrls = chains.filter { it.assets.any { it.symbolToShow == chainAsset.symbolToShow } }
                    .associate { it.chain.id to it.chain.icon }

                val stateItem = assetStates.find { it.displayName == chainAsset.symbolToShow }
                if (stateItem == null) {
                    val assetListItemViewState = AssetListItemViewState(
                        assetIconUrl = chainAsset.iconUrl,
                        assetChainName = chain?.name.orEmpty(),
                        assetSymbol = chainAsset.symbol,
                        displayName = chainAsset.symbolToShow,
                        assetTokenFiat = token.fiatRate?.formatAsCurrency(token.fiatSymbol),
                        assetTokenRate = token.recentRateChange?.formatAsChange(),
                        assetBalance = assetWithStatus.asset.total.orZero().format(),
                        assetBalanceFiat = token.fiatRate?.multiply(assetWithStatus.asset.total.orZero())?.formatAsCurrency(token.fiatSymbol),
                        assetChainUrls = assetChainUrls,
                        chainId = chain?.id.orEmpty(),
                        chainAssetId = chainAsset.id,
                        isSupported = isSupported,
                        isHidden = !assetWithStatus.asset.enabled,
                        hasAccount = assetWithStatus.hasAccount,
                        priceId = chainAsset.priceId,
                        hasNetworkIssue = hasNetworkIssue
                    )

                    assetStates.add(assetListItemViewState)
                }
            }
        assetStates
    }

    private fun defaultAssetListSort() = compareByDescending<AssetWithStatus> { it.asset.total.orZero() > BigDecimal.ZERO }
        .thenByDescending { it.asset.fiatAmount.orZero() }
        .thenBy { it.asset.token.configuration.isTestNet }
        .thenBy { it.asset.token.configuration.chainId.defaultChainSort() }
        .thenBy { it.asset.token.configuration.chainName }

    val state = combine(
        assetStates,
        assetTypeSelectorState.asFlow(),
        balanceLiveData.asFlow(),
        hiddenAssetsState.asFlow(),
        enteredChainQueryFlow
    ) { assetsListItemStates: List<AssetListItemViewState>,
        multiToggleButtonState: MultiToggleButtonState<AssetType>,
        balanceModel: BalanceModel,
        hiddenState: HiddenItemState,
        selectedChainId: String? ->

        if (assetsListItemStates.isEmpty() || balanceModel.isShowLoading) {
            return@combine LoadingState.Loading()
        }

        val balanceState = AssetBalanceViewState(
            balance = balanceModel.totalBalance?.formatAsCurrency(balanceModel.fiatSymbol).orEmpty(),
            address = "",
            changeViewState = ChangeBalanceViewState(
                percentChange = balanceModel.rate?.formatAsChange().orEmpty(),
                fiatChange = balanceModel.totalBalanceChange.abs().formatAsCurrency(balanceModel.fiatSymbol)
            )
        )

        val hasNetworkIssues = assetsListItemStates.any { !it.hasAccount || it.hasNetworkIssue }

        LoadingState.Loaded(
            WalletState(
                multiToggleButtonState,
                assetsListItemStates,
                balanceState,
                hiddenState,
                hasNetworkIssues
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    val toolbarState = combine(currentAddressModelFlow(), selectedChainItem) { addressModel, chain ->
        LoadingState.Loaded(
            MainToolbarViewState(
                title = addressModel.nameOrAddress,
                homeIconState = ToolbarHomeIconState(walletIcon = addressModel.image),
                selectorViewState = ChainSelectorViewState(chain?.title, chain?.id)
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    private val itemsToFillTheMostScreens = 7
    val assetShimmerItems = assetModelsFlow().take(itemsToFillTheMostScreens)
        .mapList {
            AssetListItemShimmerViewState(
                assetIconUrl = it.token.configuration.iconUrl,
                assetChainUrls = listOf(it.token.configuration.iconUrl)
            )
        }
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = defaultWalletShimmerItems())

    private fun defaultWalletShimmerItems(): List<AssetListItemShimmerViewState> = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/SORA.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Bifrost.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    ).map { iconUrl ->
        AssetListItemShimmerViewState(
            assetIconUrl = iconUrl,
            assetChainUrls = listOf(iconUrl)
        )
    }

    fun sync() {
        viewModelScope.launch {
            getAvailableFiatCurrencies.sync()

            val result = interactor.syncAssetsRates()

            result.exceptionOrNull()?.let(::showError)
            _hideRefreshEvent.value = Event(Unit)
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
        val payload = AssetPayload(chainId, chainAssetId)
        launch {
            swipeableState.snapTo(SwipeState.INITIAL)
        }
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
            ActionItemType.HIDE -> {
                launch { hideAsset(chainId, chainAssetId) }
            }
            ActionItemType.SHOW -> {
                launch { showAsset(chainId, chainAssetId) }
            }
            else -> {}
        }
    }

    suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    fun sendClicked(assetPayload: AssetPayload) {
        router.openChooseRecipient(assetPayload)
    }

    fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    fun assetClicked(asset: AssetListItemViewState) {
        if (asset.hasNetworkIssue) {
            launch {
                val chain = interactor.getChain(asset.chainId)
                if (chain.nodes.size > 1) {
                    router.openNodes(asset.chainId)
                } else {
                    router.openNetworkUnavailable(chain.name)
                }
            }
            return
        }
        if (!asset.hasAccount) {
            launch {
                val meta = accountRepository.getSelectedMetaAccount()
                val payload = AddAccountBottomSheet.Payload(
                    metaId = meta.id,
                    chainId = asset.chainId,
                    chainName = asset.assetChainName,
                    assetId = asset.chainAssetId,
                    priceId = asset.priceId,
                    markedAsNotNeed = false
                )
                router.openOptionsAddAccount(payload)
            }
            return
        }
        if (asset.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
            return
        }

        val payload = AssetPayload(
            chainId = asset.chainId,
            chainAssetId = asset.chainAssetId
        )

        router.openAssetDetails(payload)
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

    fun onHiddenAssetClicked() {
        hiddenAssetsState.value = HiddenItemState(
            isExpanded = hiddenAssetsState.value?.isExpanded?.not() ?: false
        )
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

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
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

    private fun assetWarningFlow(): Flow<Boolean> =
        interactor.assetsFlow()
            .map { list ->
                list.any {
                    !it.hasAccount && !it.asset.markedNotNeed
                }
            }

    fun manageAssetsClicked() {
        router.openManageAssets()
    }

    fun onBalanceClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value = FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
        }
    }

    fun onNetworkIssuesClicked() {
        router.openNetworkIssues()
    }

    fun onFiatSelected(item: FiatCurrency) {
        viewModelScope.launch {
            selectedFiat.set(item.id)
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    fun assetTypeChanged(type: AssetType) {
        assetTypeSelectorState.value = assetTypeSelectorState.value?.copy(currentSelection = type)
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val result = interactor.getRecipientFromQrCodeContent(content).getOrDefault(content)

            _decodeAddressResult.value = Event(result)
        }
    }

    fun openWalletSelector() {
        router.openSelectWallet()
    }

    fun openSearchAssets() {
        router.openSearchAssets(selectedChainItem.value?.id)
    }
}
