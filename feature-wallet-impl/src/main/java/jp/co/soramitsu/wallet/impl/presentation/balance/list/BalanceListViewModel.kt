package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.widget.LinearLayout
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import com.walletconnect.android.internal.common.exception.MalformedWalletConnectUri
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewStateWithFilters
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.domain.FiatCurrencies
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.NetworkStateUi
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.presentation.NftRouter
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilterModel
import jp.co.soramitsu.nft.impl.presentation.filters.NftFiltersFragment
import jp.co.soramitsu.nft.impl.presentation.list.models.NFTCollectionsScreenModel
import jp.co.soramitsu.nft.impl.presentation.list.models.NFTCollectionsScreenView
import jp.co.soramitsu.nft.impl.presentation.list.utils.createShimmeredNFTCollectionsViewsArray
import jp.co.soramitsu.nft.impl.presentation.list.utils.toScreenViewsArray
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.runtime.ext.ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.pendulumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItemViewState
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_WALLET_CONNECT
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetListHelper
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import jp.co.soramitsu.wallet.impl.presentation.model.ControllerDeprecationWarningModel
import jp.co.soramitsu.wallet.impl.presentation.model.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CURRENT_ICON_SIZE = 40

@HiltViewModel
class BalanceListViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val soraCardInteractor: SoraCardInteractor,
    private val chainInteractor: ChainInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val accountInteractor: AccountInteractor,
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val getTotalBalance: TotalBalanceUseCase,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val nftRouter: NftRouter,
    private val nftInteractor: NFTInteractor
    private val walletConnectInteractor: WalletConnectInteractor
) : BaseViewModel(), UpdatesProviderUi by updatesMixin, NetworkStateUi by networkStateMixin,
    WalletScreenInterface {

    private val accountAddressToChainIdMap = mutableMapOf<String, ChainId?>()

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val _launchSoraCardSignIn = MutableLiveData<Event<SoraCardContractData>>()
    val launchSoraCardSignIn: LiveData<Event<SoraCardContractData>> = _launchSoraCardSignIn

    private val mutableScreenLayoutFlow = MutableStateFlow(ScreenLayout.Grid)

    private val mutableNFTPaginationRequestFlow = MutableSharedFlow<PaginationRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val pageScrollingCallback = object : PageScrollingCallback {
        override fun onAllPrevPagesScrolled() {
            mutableNFTPaginationRequestFlow.tryEmit(PaginationRequest.Prev.Page)
        }

        override fun onAllNextPagesScrolled() {
            mutableNFTPaginationRequestFlow.tryEmit(PaginationRequest.Next.Page)
        }
    }

    private val chainsFlow = chainInteractor.getChainsFlow().mapList {
        it.toChainItemState()
    }.inBackground()

    private val selectedChainId = MutableStateFlow<ChainId?>(null)
    private val selectedChainItemFlow =
        combine(selectedChainId, chainsFlow) { selectedChainId, chains ->
            selectedChainId?.let {
                chains.firstOrNull { it.id == selectedChainId }
            }
        }

    private val currentMetaAccountFlow = interactor.selectedLightMetaAccountFlow()
        .onEach {
            if (pendulumPreInstalledAccountsScenario.isPendulumMode(it.id)) {
                selectedChainId.value = pendulumChainId
            }

        }

    private val assetTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = AssetType.Currencies,
            toggleStates = AssetType.values().toList()
        )
    )

    private val showNetworkIssues = MutableStateFlow(false)

    private val assetStates = combine(
        interactor.assetsFlow(),
        chainInteractor.getChainsFlow(),
        selectedChainId,
        interactor.selectedMetaAccountFlow(),
        networkIssuesFlow,
        interactor.observeSelectedAccountChainSelectFilter(),
        interactor.observeHideZeroBalanceEnabledForCurrentWallet()
    ) {
            assets: List<AssetWithStatus>,
            chains: List<Chain>,
            selectedChainId: ChainId?,
            currentMetaAccountFlow: MetaAccount,
            networkIssues: Set<NetworkIssueItemState>,
            appliedFilterAsString: String,
            hideZeroBalancesEnabled: Boolean ->

        val filter = ChainSelectorViewStateWithFilters.Filter.values().find {
            it.name == appliedFilterAsString
        } ?: ChainSelectorViewStateWithFilters.Filter.All

        val shouldShowNetworkIssues =
            selectedChainId == null && (networkIssues.isNotEmpty() || assets.any { it.hasAccount.not() })
        showNetworkIssues.value = shouldShowNetworkIssues

        val selectedAccountFavoriteChains = currentMetaAccountFlow.favoriteChains

        val chainsWithFavoriteInfo = chains.map { chain ->
            chain to (selectedAccountFavoriteChains[chain.id]?.isFavorite == true)
        }

        val filteredChains = when {
            selectedChainId != null -> chains.filter { it.id == selectedChainId }
            filter == ChainSelectorViewStateWithFilters.Filter.All -> chainsWithFavoriteInfo.map { it.first }

            filter == ChainSelectorViewStateWithFilters.Filter.Favorite ->
                chainsWithFavoriteInfo.filter { (_, isFavorite) -> isFavorite }.map { it.first }

            filter == ChainSelectorViewStateWithFilters.Filter.Popular ->
                chainsWithFavoriteInfo.filter { (chain, _) ->
                    chain.rank != null
                }.sortedBy { (chain, _) ->
                    chain.rank
                }.map { it.first }

            else -> emptyList()
        }

        val filteredAssets = assets
            .filter {
                selectedChainId == it.asset.token.configuration.chainId ||
                        selectedChainId == null ||
                        it.asset.token.configuration.chainId in filteredChains.map { it.id }
            }

        val balanceListItems = AssetListHelper.processAssets(
            assets = filteredAssets,
            filteredChains = filteredChains,
            selectedChainId = selectedChainId,
            networkIssues = networkIssues,
            hideZeroBalancesEnabled = hideZeroBalancesEnabled
        )

        val assetStates: List<AssetListItemViewState> = balanceListItems
            .sortedWith(defaultBalanceListItemSort())
            .map { it.toAssetState() }

        assetStates
    }.onStart { emit(buildInitialAssetsList().toMutableList()) }.inBackground().share()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun createNFTCollectionScreenViewsFlow(): Flow<ArrayDeque<NFTCollectionsScreenView>> {
        val pullToRefreshHelperFlow = BalanceUpdateTrigger.observe()
            .map { PaginationRequest.Next.Page }

        val paginationRequestHelperFlow = merge(
            mutableNFTPaginationRequestFlow,
            pullToRefreshHelperFlow
        ).onStart {
            emit(PaginationRequest.Start)
        }.debounce(10_000)

        return nftInteractor.userOwnedNFTsFlow(
            paginationRequestFlow = paginationRequestHelperFlow,
            chainSelectionFlow = selectedChainId
        ).combine(mutableScreenLayoutFlow) { allNFTCollectionsData, screenLayout ->
            return@combine allNFTCollectionsData to screenLayout
        }.transformLatest { (allNFTCollectionsData, screenLayout) ->
            if (
                allNFTCollectionsData.isNotEmpty() &&
                allNFTCollectionsData.all { (_, result) -> result.isFailure }
            ) {
                showError("Failed to load NFTs")
                return@transformLatest emit(
                    ArrayDeque<NFTCollectionsScreenView>().apply {
                        add(NFTCollectionsScreenView.EmptyPlaceHolder.DEFAULT)
                    }
                )
            }

            if (allNFTCollectionsData.any { (_, result) -> result.isFailure }) {
                val joinedChainsWithFailedRequests =
                    allNFTCollectionsData.mapNotNull { (chain, result) ->
                        if (result.isSuccess)
                            return@mapNotNull null

                        return@mapNotNull chain.name
                    }.joinToString(", ")

                showError("Failed to load NFTs for $joinedChainsWithFailedRequests")
            }

            val successfulNFTCollectionsList =
                allNFTCollectionsData.filter { (_, collectionRequestResult) ->
                    collectionRequestResult.isSuccess
                }.mapNotNull { (_, collectionRequestResult) ->
                    collectionRequestResult.getOrNull()
                }.cast<List<NFTCollection<NFTCollection.NFT.Light>>>()

            return@transformLatest successfulNFTCollectionsList
                .toScreenViewsArray(
                    screenLayout = screenLayout,
                    onItemClick = { router.openNftCollection(it.chainId, it.contractAddress!!) }
                ).run { emit(this) }
        }.onStart {
            createShimmeredNFTCollectionsViewsArray(
                ScreenLayout.Grid
            ).also { emit(it) }

            delay(10_000) // wait till internal flows start
        }
    }

    private val defaultFiltersState =
        NftFilterModel(mapOf(NFTFilter.Spam to true, NFTFilter.Airdrops to false))

    private val filtersFlow = nftRouter.nftFiltersResultFlow(NftFiltersFragment.KEY_RESULT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultFiltersState)

    private val assetTypeState = combine(
        assetTypeSelectorState,
        assetStates,
        nftInteractor.nftFiltersFlow(),
        createNFTCollectionScreenViewsFlow()
    ) { selectorState, assetStates, filters, views ->
        when (selectorState.currentSelection) {
            AssetType.Currencies -> {
                WalletAssetsState.Assets(assetStates)
            }

            AssetType.NFTs -> {
                WalletAssetsState.NftAssets(
                    NFTCollectionsScreenModel(
                        areFiltersApplied = filters.any { (_, isEnabled) -> isEnabled },
                        viewsArray = views,
                        onFiltersIconClick = { nftRouter.openNftFilters(filtersFlow.value) },
                        onScreenLayoutChanged = { mutableScreenLayoutFlow.value = it },
                        pageScrollingCallback = pageScrollingCallback
                    )
                )
            }
        }
    }

    private fun observeFiatSymbolChange() {
        combine(
            selectedFiat.flow(),
            getAvailableFiatCurrencies.flow()
        ) { selectedFiat: String, fiatCurrencies: FiatCurrencies ->
            fiatCurrencies.associateBy { it.id }[selectedFiat]?.symbol
        }
            .mapNotNull { it }
            .onEach {
                sync()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    }

    private fun observeNetworkState() {
        networkStateMixin.showConnectingBarFlow
            .onEach { hasConnectionProblems ->
                if (!hasConnectionProblems) {
                    refresh()
                }
            }
            .launchIn(viewModelScope)
    }

    // we open screen - no assets in the list
    private suspend fun buildInitialAssetsList(): List<AssetListItemViewState> {
        return withContext(Dispatchers.Default) {
            val assets = chainInteractor.getRawChainAssets()

            assets.sortedWith(defaultChainAssetListSort()).map { chainAsset ->
                AssetListItemViewState(
                    assetIconUrl = chainAsset.iconUrl,
                    assetChainName = chainAsset.chainName,
                    assetName = chainAsset.name.orEmpty(),
                    assetSymbol = chainAsset.symbol,
                    assetTokenFiat = null,
                    assetTokenRate = null,
                    assetTransferableBalance = null,
                    assetTransferableBalanceFiat = null,
                    assetChainUrls = emptyMap(),
                    chainId = chainAsset.chainId,
                    chainAssetId = chainAsset.id,
                    isSupported = true,
                    isHidden = false,
                    priceId = chainAsset.priceId,
                    isTestnet = chainAsset.isTestNet ?: false
                )
            }.filter { selectedChainId.value == null || selectedChainId.value == it.chainId }
        }
    }

    private fun defaultBalanceListItemSort() =
        compareByDescending<BalanceListItemModel> { it.total > BigDecimal.ZERO }
            .thenByDescending { it.fiatAmount.orZero() }
            .thenBy { it.asset.isTestNet }
            .thenBy { it.asset.chainId.defaultChainSort() }
            .thenBy { it.asset.chainName }

    private fun defaultChainAssetListSort() = compareBy<Asset> { it.isTestNet }
        .thenBy { it.chainId.defaultChainSort() }
        .thenBy { it.chainName }

    //    private val soraCardState = combine(
//        interactor.observeIsShowSoraCard(),
//        soraCardInteractor.subscribeSoraCardInfo()
//    ) { isShow, soraCardInfo ->
//        val kycStatus = soraCardInfo?.kycStatus?.let(::mapKycStatus)
//        SoraCardItemViewState(kycStatus, soraCardInfo, null, isShow)
//    }
    private val soraCardState = flowOf(SoraCardItemViewState())

    val state = MutableStateFlow(WalletState.default)

    private fun subscribeScreenState() {
        assetStates.onEach {
            state.value = state.value.copy(assets = it)
        }.launchIn(this)

        assetTypeSelectorState.onEach {
            state.value = state.value.copy(multiToggleButtonState = it)
        }.launchIn(this)

        soraCardState.onEach {
            state.value = state.value.copy(soraCardState = it)
        }.launchIn(this)

        currentMetaAccountFlow.onEach {
            state.value = state.value.copy(isBackedUp = it.isBackedUp)
        }.launchIn(this)

        showNetworkIssues.onEach {
            state.value = state.value.copy(hasNetworkIssues = it)
        }.launchIn(this)
        subscribeTotalBalance()
    }

    private fun subscribeTotalBalance() {
        combine(
            selectedChainId.map { chainId -> chainId?.let { currentAccountAddress(it) }.orEmpty() },
            interactor.selectedLightMetaAccountFlow().flatMapLatest {
                getTotalBalance.observe()
            }
        ) { selectedChainAddress, balanceModel ->
            AssetBalanceViewState(
                transferableBalance = balanceModel.balance.formatFiat(balanceModel.fiatSymbol),
                address = selectedChainAddress,
                changeViewState = ChangeBalanceViewState(
                    percentChange = balanceModel.rateChange?.formatAsChange().orEmpty(),
                    fiatChange = balanceModel.balanceChange.abs()
                        .formatFiat(balanceModel.fiatSymbol)
                )
            )
        }.onEach {
            state.value = state.value.copy(balance = it)
        }.launchIn(this)
    }

    val toolbarState = combine(
        currentAddressModelFlow(),
        interactor.observeSelectedAccountChainSelectFilter(),
        selectedChainItemFlow
    ) { addressModel, filter, chain ->
        LoadingState.Loaded(
            MainToolbarViewStateWithFilters(
                title = addressModel.nameOrAddress,
                homeIconState = ToolbarHomeIconState(walletIcon = addressModel.image),
                selectorViewState = ChainSelectorViewStateWithFilters(
                    selectedChainName = chain?.title,
                    selectedChainId = chain?.id,
                    selectedChainImageUrl = chain?.imageUrl,
                    filterApplied = ChainSelectorViewStateWithFilters.Filter.values().find {
                        it.name == filter
                    } ?: ChainSelectorViewStateWithFilters.Filter.All
                )
            )
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = LoadingState.Loading()
    )

    init {
        subscribeScreenState()
        observeNetworkState()
        observeFiatSymbolChange()

        router.chainSelectorPayloadFlow.map { chainId ->
            val walletId = interactor.getSelectedMetaAccount().id
            interactor.saveChainId(walletId, chainId)
            selectedChainId.value = chainId
        }.launchIn(this)

        interactor.selectedLightMetaAccountFlow().map { wallet ->
            if (pendulumPreInstalledAccountsScenario.isPendulumMode(wallet.id)) {
                selectedChainId.value = pendulumChainId
            } else {
                selectedChainId.value = interactor.getSavedChainId(wallet.id)
            }
        }.launchIn(this)

        if (!interactor.isShowGetSoraCard()) {
            interactor.decreaseSoraCardHiddenSessions()
        }
    }

    override fun onRefresh() {
        refresh()
        viewModelScope.launch {
            BalanceUpdateTrigger.invoke()
        }
    }

    private fun refresh() {
        sync()
    }

    fun onResume() {
        viewModelScope.launch {
            interactor.selectedMetaAccountFlow().collect {
                checkControllerDeprecations()
            }
            checkControllerDeprecations()
        }
    }

    private suspend fun checkControllerDeprecations() {
        val warnings = withContext(Dispatchers.Default) { interactor.checkControllerDeprecations() }
        warnings.firstOrNull()?.let { warning ->
            val model = warning.toModel(resourceManager)
            showError(
                title = model.title,
                message = model.message,
                positiveButtonText = model.buttonText,
                negativeButtonText = null,
                positiveClick = {
                    when (model.action) {
                        ControllerDeprecationWarningModel.Action.ChangeController -> {
                            router.openManageControllerAccount(model.chainId)
                        }

                        ControllerDeprecationWarningModel.Action.ImportStash -> {
                            router.openImportAccountScreenFromWallet(0)
                        }
                    }
                }
            )
        }
    }

    private fun sync() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                getAvailableFiatCurrencies.sync()
                interactor.syncAssetsRates()
            }

            result.exceptionOrNull()?.let(::showError)
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun actionItemClicked(
        actionType: ActionItemType,
        chainId: ChainId,
        chainAssetId: String,
        swipeableState: SwipeableState<SwipeState>
    ) {
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

    private suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    private suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    override fun assetClicked(state: AssetListItemViewState) {
        launch {
            if (state.isSupported.not()) {
                _showUnsupportedChainAlert.value = Event(Unit)
                return@launch
            }

            router.openAssetIntermediateDetails(state.chainAssetId)
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedLightMetaAccountFlow()
            .map {
                val polkadotAddressPrefix = 0
                val address = it.substrateAccountId.toAddress(polkadotAddressPrefix.toShort())
                WalletAccount(address, it.name)
            }
            .catch { emit(WalletAccount("", "")) }
            .onEach { account ->
                if (accountAddressToChainIdMap.containsKey(account.address).not()) {
                    selectedChainId.value = null
                    accountAddressToChainIdMap[account.address] = null
                } else {
                    selectedChainId.value =
                        accountAddressToChainIdMap.getOrDefault(account.address, null)
                }
            }
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    override fun onBalanceClicked() {
        viewModelScope.launch {
            val currencies = getAvailableFiatCurrencies()
            if (currencies.isEmpty()) return@launch
            val selected = selectedFiat.get()
            val selectedItem = currencies.first { it.id == selected }
            _showFiatChooser.value =
                FiatChooserEvent(DynamicListBottomSheet.Payload(currencies, selectedItem))
        }
    }

    override fun onNetworkIssuesClicked() {
        router.openNetworkIssues()
    }

    override fun onBackupClicked() {
        launch {
            val selectedMetaAccount = accountInteractor.selectedLightMetaAccount()
            router.openBackupWalletScreen(selectedMetaAccount.id)
        }
    }

    override fun onBackupCloseClick() {
        showError(
            title = resourceManager.getString(jp.co.soramitsu.feature_account_impl.R.string.backup_not_backed_up_title),
            message = resourceManager.getString(jp.co.soramitsu.feature_account_impl.R.string.backup_not_backed_up_message),
            positiveButtonText = resourceManager.getString(jp.co.soramitsu.feature_account_impl.R.string.backup_not_backed_up_confirm),
            negativeButtonText = resourceManager.getString(jp.co.soramitsu.feature_account_impl.R.string.common_cancel),
            buttonsOrientation = LinearLayout.HORIZONTAL,
            positiveClick = { considerWalletBackedUp() }
        )
    }

    private fun considerWalletBackedUp() {
        launch {
            val meta = accountInteractor.selectedLightMetaAccount()
            accountInteractor.updateWalletBackedUp(meta.id)
        }
    }

    override fun onAddressClick() {
        launch {
            selectedChainId.value?.let {
                currentAccountAddress(chainId = it)
            }?.let { address ->
                copyToClipboard(address)
            }
        }
    }

    override fun soraCardClicked() {
        if (state.value.soraCardState?.kycStatus == null) {
            router.openGetSoraCard()
        } else {
            onSoraCardStatusClicked()
        }
    }

    override fun soraCardClose() {
        interactor.hideSoraCard()
    }

    fun onFiatSelected(item: FiatCurrency) {
        viewModelScope.launch {
            selectedFiat.set(item.id)
        }
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    override fun assetTypeChanged(type: AssetType) {
        assetTypeSelectorState.value = assetTypeSelectorState.value.copy(currentSelection = type)
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            if (content.startsWith(QR_PREFIX_WALLET_CONNECT)) {
                sendWalletConnectPair(pairingUri = content)
            } else {
                val cbdcFormat = interactor.tryReadCBDCAddressFormat(content)
                if (cbdcFormat != null) {
                    router.openCBDCSend(cbdcQrInfo = cbdcFormat)
                } else {
                    val soraFormat =
                        interactor.tryReadSoraFormat(content)
                    if (soraFormat != null) {
                        val amount =
                            soraFormat.amount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                        openSendSoraTokenTo(soraFormat.tokenId, soraFormat.address, amount)
                    } else {
                        router.openSend(
                            assetPayload = null,
                            initialSendToAddress = content,
                            amount = null
                        )
                    }
                }
            }
        }
    }

    private fun sendWalletConnectPair(pairingUri: String) {
        walletConnectInteractor.pair(
            pairingUri = pairingUri,
            onError = { error ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (error.throwable is MalformedWalletConnectUri) {
                        showError(
                            title = resourceManager.getString(R.string.connection_invalid_url_error_title),
                            message = resourceManager.getString(R.string.connection_invalid_url_error_message),
                            positiveButtonText = resourceManager.getString(R.string.common_close)
                        )
                    } else {
                        showError(error.throwable.message ?: "WalletConnect pairing error")
                    }
                }
            }
        )
    }

    private suspend fun openSendSoraTokenTo(
        qrTokenId: String,
        soraAddress: String,
        amount: BigDecimal?
    ) {
        val soraChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        val soraChain = interactor.getChains().first().firstOrNull {
            it.id == soraChainId
        }
        val soraAsset = soraChain?.assets?.firstOrNull {
            it.currencyId == qrTokenId
        }
        val payloadFromQr = soraAsset?.let {
            AssetPayload(it.chainId, it.id)
        }

        router.openLockedAmountSend(
            assetPayload = payloadFromQr,
            initialSendToAddress = soraAddress,
            currencyId = qrTokenId,
            amount = amount
        )
    }

    fun openWalletSelector() {
        router.openSelectWallet()
    }

    fun openSearchAssets() {
        router.openSearchAssets()
    }

    fun openSelectChain() {
        router.openSelectChain(selectedChainId.value, isFilteringEnabled = true)
    }

    private fun copyToClipboard(text: String) {
        clipboardManager.addToClipboard(text)

        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    private fun onSoraCardStatusClicked() {
    }
}
