package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.net.Uri
import android.util.Log
import android.widget.LinearLayout
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import com.reown.android.internal.common.exception.MalformedWalletConnectUri
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.account.CapturedPortfolioHeader
import jp.co.soramitsu.account.api.presentation.account.capturedPortfolioHeaderFlow
import jp.co.soramitsu.account.api.presentation.account.portfolioBalanceCaption
import jp.co.soramitsu.account.api.presentation.account.presentTotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.MainToolbarViewStateWithFilters
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatChooserEvent
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.storage.UpdateSecurityNoticeTracker
import jp.co.soramitsu.common.domain.FiatCurrencies
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.greaterThanOrEquals
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.lessThan
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.runtime.multiNetwork.chain.model.pendulumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.TonConnectException
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_TON_CONNECT
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_WALLET_CONNECT
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetListHelper
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.toChainItemState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenModel
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenView
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.ScreenModel
import jp.co.soramitsu.wallet.impl.presentation.model.ControllerDeprecationWarningModel
import jp.co.soramitsu.wallet.impl.presentation.model.toModel
import jp.co.soramitsu.wallet.impl.presentation.send.showSendPausedSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val CURRENT_ICON_SIZE = 40

internal fun forcedPortfolioChainId(
    isPendulumMode: Boolean,
    supportedEcosystems: Set<WalletEcosystem>
): ChainId? = when {
    isPendulumMode -> pendulumChainId
    supportedEcosystems == setOf(WalletEcosystem.Ton) -> tonMainnetChainId
    else -> null
}

@HiltViewModel
class BalanceListViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val selectedFiat: SelectedFiat,
    private val accountInteractor: AccountInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val getTotalBalance: TotalBalanceUseCase,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val nftInteractor: NFTInteractor,
    private val walletConnectInteractor: WalletConnectInteractor,
    private val coroutineManager: CoroutineManager,
    private val tonConnectInteractor: TonConnectInteractor,
    private val networkScanStateStore: NetworkScanStateStore,
    private val assetMetadataDescriptorStore: AssetMetadataDescriptorStore,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val sendPausePolicy: SendPausePolicy,
    private val portfolioVisibility: PortfolioVisibility,
    private val portfolioPreferences: PortfolioPresentationPreferences,
    private val tokenGroupRegistry: jp.co.soramitsu.runtime.multiNetwork.tokengroups.TokenGroupRegistry,
    private val updateSecurityNotice: UpdateSecurityNoticeTracker,
) : BaseViewModel(), WalletScreenInterface {

    private var awaitAssetsJob: Job? = null

    private val _showFiatChooser = MutableLiveData<FiatChooserEvent>()
    val showFiatChooser: LiveData<FiatChooserEvent> = _showFiatChooser

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _showUpdateSecurityNotice = MutableLiveData<Event<Unit>>()
    val showUpdateSecurityNotice: LiveData<Event<Unit>> = _showUpdateSecurityNotice

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val mutableScreenLayoutFlow = MutableStateFlow(ScreenLayout.Grid)

    private val mutableNFTPaginationRequestFlow = MutableSharedFlow<PaginationRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val pageScrollingCallback = object : PageScrollingCallback {
        override fun onAllPrevPagesScrolled() {
            mutableNFTPaginationRequestFlow.tryEmit(PaginationRequest.Prev)
        }

        override fun onAllNextPagesScrolled() {
            mutableNFTPaginationRequestFlow.tryEmit(PaginationRequest.Next(100))
        }
    }

    private val currentMetaAccountFlow = accountInteractor.selectedLightMetaAccountFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentWalletRecoveryStateFlow = currentMetaAccountFlow.flatMapLatest { metaAccount ->
        accountInteractor.walletRecoveryRequiredFlow(metaAccount.id).map { isRecoveryRequired ->
            metaAccount to isRecoveryRequired
        }
    }

    private val chainsFlow = chainInteractor.getChainsFlow()
        .map { chains -> chains.map { it.toChainItemState() } }
        .inBackground()

    private val selectedChainId = MutableStateFlow<ChainId?>(null)
    private val allowSelectChain = MutableStateFlow(false)

    private val selectedChainItemFlow =
        combine(selectedChainId, chainsFlow) { selectedChainId, chains ->
            allowSelectChain.value = !((selectedChainId == null && chains.size == 1) || selectedChainId == tonMainnetChainId)
            selectedPortfolioChainItem(selectedChainId, chains)
        }

    private val networkIssueStateFlow = MutableStateFlow<WalletAssetsState.NetworkIssue?>(null)

    private val assetTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = AssetType.Currencies,
            toggleStates = AssetType.entries
        )
    )

    private val showNetworkIssues = MutableStateFlow(false)

    private val currentAssetsFlow = MutableStateFlow<List<AssetWithStatus>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val showTestnetsFlow = currentMetaAccountFlow
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeShowTestnets)

    private val baseAssetStates = combine(
        interactor.assetsFlowAndAccount(),
        chainInteractor.getChainsFlow(),
        selectedChainId,
        interactor.selectedMetaAccountFlow(),
        interactor.observeSelectedAccountChainSelectFilter(),
        showTestnetsFlow
    )  { (walletId: Long, assets: List<AssetWithStatus>),
        chains: List<Chain>,
        selectedChainId: ChainId?,
        currentMetaAccount: MetaAccount,
        filter: ChainSelectorViewStateWithFilters.Filter,
        showTestnets: Boolean ->

        if (currentMetaAccount.id != walletId) {
            return@combine AssetsLoadingState.Loading()
        }

        // The first list of a wallet in this version holds exactly what earlier versions left hidden.
        portfolioPreferences.rememberLegacyHiddenAssets(walletId) {
            assets.filter { it.asset.enabled == false }.mapTo(mutableSetOf()) { (asset, _, _) ->
                PortfolioPresentationPreferences.assetKey(asset.token.configuration.chainId, asset.token.configuration.id)
            }
        }

        val selectedAccountFavoriteChains = currentMetaAccount.favoriteChains
        val chainsById = chains.associateBy(Chain::id)

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
                    chain.rank != null && !portfolioVisibility.isTestnet(chain)
                }.sortedBy { (chain, _) ->
                    chain.rank
                }.map { it.first }

            else -> emptyList()
        }

        val scopedAssets = assets.filter { (asset, _, _) ->
            (selectedChainId == null && filter == ChainSelectorViewStateWithFilters.Filter.All) ||
                asset.token.configuration.chainId == selectedChainId ||
                filteredChains.any { it.id == asset.token.configuration.chainId }
        }

        val filteredAssets = scopedAssets.filter { (asset, _, _) ->
            val chainId = asset.token.configuration.chainId
            val chain = chainsById[chainId] ?: return@filter false

            portfolioVisibility.isVisible(
                enabled = asset.enabled,
                total = asset.total,
                chain = chain,
                asset = asset.token.configuration,
                isFavourite = selectedAccountFavoriteChains[chainId]?.isFavorite == true,
                isOnlySelectedNetwork = chainId == selectedChainId,
                isSendVetoed = sendPausePolicy.isPaused(chain),
                showTestnets = showTestnets
            )
        }

        currentAssetsFlow.update { filteredAssets }

        val filteredAssetsIncludingUnavailable = filteredAssets

        // Subtotals, network order and the header total count the same set: every holding that is
        // neither hidden nor on a testnet, independent of the network display filter.
        val accountingAssets = assets.filter {
            val chain = chainsById[it.asset.token.configuration.chainId]
            chain != null && portfolioVisibility.isCountedInTotals(it.asset.enabled, chain)
        }
        val accountingItems = AssetListHelper.processAssets(
            assets = accountingAssets,
            filteredChains = chains,
            metadataDescriptor = assetMetadataDescriptorStore::get,
            isTestnet = portfolioVisibility::isTestnet,
            hasTrustedPrice = portfolioVisibility::hasTrustedPrice
        )
        val accountingItemsByNetwork = accountingItems.groupBy { it.canonicalIdentity.chainId }

        val balanceListItems = AssetListHelper.processAssets(
            assets = filteredAssetsIncludingUnavailable,
            filteredChains = filteredChains,
            selectedChainId = selectedChainId,
            metadataDescriptor = assetMetadataDescriptorStore::get,
            isTestnet = portfolioVisibility::isTestnet,
            hasTrustedPrice = portfolioVisibility::hasTrustedPrice
        )

        val presentedItems = AssetListHelper.filterForPortfolioPresentation(
            assets = balanceListItems,
            assetDiscoveryShadowEnabled = featureToggleStore.assetDiscoveryShadowEnabled
        )
        val networkOrderedItems = AssetListHelper.sortByNetwork(presentedItems, accountingItems)
        val itemsByNetwork = networkOrderedItems.groupBy { it.canonicalIdentity.chainId }

        val assetStates: List<AssetListItemViewState> = networkOrderedItems
            .mapIndexed { index, item ->
                val networkItems = itemsByNetwork.getValue(item.canonicalIdentity.chainId)
                val accountingNetworkItems = accountingItemsByNetwork[item.canonicalIdentity.chainId].orEmpty()
                val networkFiatSubtotal = AssetListHelper.trustedFiatSubtotal(accountingNetworkItems)
                    ?.formatFiat(item.token.fiatSymbol)
                val networkAccountLabel = item.chain
                    ?.let(currentMetaAccount::address)
                    ?.shortenAddress()
                    ?: item.chainAccountName
                if (currentMetaAccount.id == walletId) {
                    item.toAssetState(index, networkFiatSubtotal, networkItems.size, resourceManager.getString(R.string.portfolio_balance_unavailable))
                        .copy(networkAccountLabel = networkAccountLabel)
                } else {
                    // invoke shimmers
                    item.toAssetState(index, networkFiatSubtotal, networkItems.size, resourceManager.getString(R.string.portfolio_balance_unavailable))
                        .copy(
                            assetTransferableBalance = null,
                            assetTotalBalance = null,
                            networkAccountLabel = networkAccountLabel
                        )
                }
            }

        // What the total leaves out is wallet-wide like the total, whatever the network filter.
        val hiddenItems = AssetListHelper.processAssets(
            assets = assets.filter { (asset, _, _) ->
                val chain = chainsById[asset.token.configuration.chainId]
                asset.enabled == false && chain != null && !portfolioVisibility.isTestnet(chain)
            },
            filteredChains = chains,
            metadataDescriptor = assetMetadataDescriptorStore::get,
            isTestnet = portfolioVisibility::isTestnet,
            hasTrustedPrice = portfolioVisibility::hasTrustedPrice
        )

        AssetsLoadingState.Loaded(
            assetStates,
            allAssetsHidden = areAllPortfolioAssetsHidden(scopedAssets.map { it.asset.enabled }),
            exclusions = AssetListHelper.portfolioExclusions(
                countedItems = accountingItems,
                hiddenItems = hiddenItems
            ),
            walletId = walletId,
            selectedNetwork = selectedChainId,
            portfolioAssets = networkOrderedItems.zip(assetStates).map { (raw, state) ->
                PortfolioAsset(state, raw.total, raw.fiatAmount, raw.token.fiatSymbol, raw.chain?.rank)
            },
            availableAssets = PortfolioGroupMembers.includeUnfunded(
                holdings = (accountingItems + hiddenItems).map { raw ->
                    PortfolioAsset(raw.toAssetState(unavailableText = resourceManager.getString(R.string.portfolio_balance_unavailable)).copy(networkAccountLabel = raw.chain?.let(currentMetaAccount::address)?.shortenAddress()),
                        raw.total, raw.fiatAmount, raw.token.fiatSymbol, raw.chain?.rank)
                }, chains = chains, groups = tokenGroupRegistry.resolve(chains),
                hasAccount = { chain -> currentMetaAccount.accountId(chain)?.let { !it.contentEquals(emptyAccountIdValue) } == true },
                accountLabel = { chain -> currentMetaAccount.address(chain)?.shortenAddress() }
            ),
            walletHasPositiveBalance = accountingItems.any {
                it.total?.signum() == 1 && it.metadataTrust == AssetMetadataTrust.Verified
            }
        )
    }.distinctUntilChanged()
        .onStart { emit(buildInitialAssetsList()) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssetsLoadingState.Loading()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val reviewedHiddenFundedAssetsFlow = currentMetaAccountFlow
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeReviewedHiddenFundedAssets)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hiddenFundedDismissalFlow = currentMetaAccountFlow.map { it.id }.distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeHiddenFundedDismissal)

    private val freshAssetStates = combine(
        baseAssetStates,
        currentMetaAccountFlow,
        networkScanStateStore.states,
        reviewedHiddenFundedAssetsFlow,
        hiddenFundedDismissalFlow
    ) { state, wallet, scanStates, reviewedHiddenFundedAssets, dismissedFingerprint ->
        if (state !is AssetsLoadingState.Loaded || state.walletId != wallet.id) return@combine AssetsLoadingState.Loading()

        state.with(
            // Announced once: again only when another hidden asset holds funds.
            hiddenFundedNoticeCount = portfolioPreferences.hiddenFundedNoticeCount(state.exclusions.hiddenFundedAssetKeys, reviewedHiddenFundedAssets, dismissedFingerprint),
            assets = state.assets.map { asset ->
                val scanState = scanStates[NetworkScanKey(wallet.id, asset.chainId)]
                asset.copy(
                    networkLastSuccessMillis = scanState?.lastSuccessMillis,
                    networkIsStale = scanState?.isStale == true,
                    networkSyncError = scanState?.errorMessage,
                    networkScanCoverage = scanState?.coverage?.name ?: when (asset.ecosystemId) {
                        "bitcoin" -> "Limited"
                        "solana", "iroha", "ton" -> "Complete"
                        else -> "CatalogOnly"
                    }
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssetsLoadingState.Loading()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val portfolioUiFlow = currentMetaAccountFlow.map { it.id }.distinctUntilChanged()
        .flatMapLatest { id -> portfolioPreferences.observeUi(id).map { id to it } }

    private val assetStates = combine(freshAssetStates, portfolioUiFlow, tokenGroupRegistry.observeResolvedGroups()) { state, preference, groups ->
        if (state !is AssetsLoadingState.Loaded || state.walletId != preference.first) return@combine AssetsLoadingState.Loading()
        val states = state.assets.associateBy { it.canonicalAssetKey }
        val visible = state.portfolioAssets.map { it.copy(state = states[it.key] ?: it.state) }
        state.with(portfolioItems = PortfolioListBuilder.build(visible, state.availableAssets.filter { it.state.isHidden },
            groups, preference.second, state.selectedNetwork, state.walletHasPositiveBalance),
            portfolioUi = preference.second, tokenGroups = groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetsLoadingState.Loading())

    @OptIn(FlowPreview::class)
    private fun createNFTCollectionScreenViewsFlow(): Flow<Pair<LoadableListPage<NFTCollectionsScreenView>, ScreenLayout>> {
        return channelFlow {
            val isLoadingCompleted = AtomicBoolean(true)

            val pullToRefreshHelperFlow = BalanceUpdateTrigger.observe()
                .map { PaginationRequest.Start(100) }

            val paginationRequestHelperFlow =
                merge(mutableNFTPaginationRequestFlow, pullToRefreshHelperFlow)
                    .onStart { emit(PaginationRequest.Start(100)) }
                    .onEach { request ->
                        val screenModel = when (request) {
                            is PaginationRequest.Start -> ScreenModel.Reloading

                            is PaginationRequest.Prev -> ScreenModel.PreviousPageLoading

                            is PaginationRequest.Next -> ScreenModel.NextPageLoading

                            is PaginationRequest.ProceedFromLastPage -> ScreenModel.NextPageLoading
                        }

                        send(screenModel to mutableScreenLayoutFlow.value)
                    }.debounce(300L)
                    .filter { isLoadingCompleted.get() }
                    .onEach { isLoadingCompleted.set(false) }
                    .shareIn(viewModelScope, SharingStarted.Eagerly, 1)

            nftInteractor.collectionsFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                // Network filters are presentation-only. Keeping discovery on the all-network
                // stream prevents a selected filter from pausing background NFT synchronization.
                chainSelectionFlow = flowOf(null)
            ).onEach {
                isLoadingCompleted.set(true)
            }.combine(mutableScreenLayoutFlow) { allNFTCollectionsData, screenLayout ->
                allNFTCollectionsData to screenLayout
            }.combine(selectedChainId) { (allNFTCollectionsData, screenLayout), selectedNetworkId ->
                val chainsWithFailedRequests = mutableSetOf<String>()

                val successfulCollections = ArrayDeque<NFTCollection.Loaded.Result>()
                    .apply {
                        allNFTCollectionsData.forEach {
                            if (selectedNetworkId != null && it is NFTCollection.Loaded && it.chainId != selectedNetworkId) {
                                return@forEach
                            }
                            if (it is NFTCollection.Reloading) {
                                send(ScreenModel.Reloading to screenLayout)
                                return@combine
                            }

                            if (it is NFTCollection.Loaded.WithFailure) {
                                chainsWithFailedRequests.add(it.chainName)
                            }

                            if (it is NFTCollection.Loaded.Result) {
                                addLast(it)
                            }
                        }
                    }

                val screenModel =
                    if (successfulCollections.isEmpty() && chainsWithFailedRequests.isNotEmpty()) {
                        // todo move error state to the ndt list screen
//                        withContext(Dispatchers.Main.immediate) {
//                            showError(resourceManager.getString(R.string.nft_load_error))
//                        }

                        ScreenModel.ReadyToRender(
                            result = successfulCollections,
                            screenLayout = screenLayout,
                            onItemClick = {}
                        )
                    } else {
                        // todo move error state to the ndt list screen
//                        if (successfulCollections.isNotEmpty() && chainsWithFailedRequests.isNotEmpty()) {
//                            withContext(Dispatchers.Main.immediate) {
//                                showError("${resourceManager.getString(R.string.nft_load_error)} (${chainsWithFailedRequests.joinToString(", ")})")
//                            }
//                        }

                        ScreenModel.ReadyToRender(
                            result = successfulCollections,
                            screenLayout = screenLayout,
                            onItemClick = ::onNFTCollectionClick
                        )
                    }

                send(screenModel to screenLayout)
            }.launchIn(this)
        }.distinctUntilChangedBy { (screenModel, screenLayout) ->
            "${screenModel::class.simpleName}::${screenLayout.name}"
        }.flowOn(coroutineManager.default)
    }

    private fun onNFTCollectionClick(collection: NFTCollection.Loaded.Result.Collection) {
        router.openNftCollection(
            collection.chainId,
            collection.contractAddress,
            collection.collectionName
        )
    }

    private val assetTypeState: Flow<WalletAssetsState> = combine(
        assetTypeSelectorState,
        assetStates,
        nftInteractor.nftFiltersFlow(),
        createNFTCollectionScreenViewsFlow(),
        networkIssueStateFlow
    ) { selectorState, assetStates, filters, (pageViews, screenLayout), networkIssueState ->
        when (selectorState.currentSelection) {
            AssetType.Currencies -> {
                val isSelectedChainHasIssues = networkIssueState != null
                if (isSelectedChainHasIssues) {
                    requireNotNull(networkIssueState)
                } else {
                    // Rows are network-scoped, so Hide acts on exactly one (chainId, assetId).
                    WalletAssetsState.Assets(
                        assets = assetStates,
                        isHideVisible = true
                    )
                }
            }

            AssetType.NFTs -> {
                WalletAssetsState.NftAssets(
                    NFTCollectionsScreenModel(
                        areFiltersApplied = filters.any { (_, isEnabled) -> isEnabled },
                        screenLayout = screenLayout,
                        loadableListPage = pageViews,
                        onFiltersIconClick = { router.openNFTFilter() },
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

    private fun observeToolbarStates() {
        currentAddressModelFlow().onEach { addressModel ->
            toolbarState.update { prevState ->
                val newWalletIconState = when (prevState.homeIconState) {
                    is ToolbarHomeIconState.Navigation -> ToolbarHomeIconState.Wallet(walletIcon = addressModel.image)
                    is ToolbarHomeIconState.Wallet -> (prevState.homeIconState as ToolbarHomeIconState.Wallet).copy(
                        walletIcon = addressModel.image
                    )
                }
                prevState.copy(
                    title = addressModel.nameOrAddress,
                    homeIconState = newWalletIconState,
                )
            }
        }.launchIn(viewModelScope)

        combine(
            interactor.observeSelectedAccountChainSelectFilter(),
            selectedChainItemFlow,
            allowSelectChain
        ) { filter, chain, allowChainSelect ->
            toolbarState.update { prevState ->
                prevState.copy(
                    selectorViewState = ChainSelectorViewStateWithFilters(
                        selectedChainName = chain?.title,
                        selectedChainId = chain?.id,
                        selectedChainImageUrl = chain?.imageUrl,
                        filterApplied = filter,
                        allowChainSelection = allowChainSelect
                    )
                )
            }
        }.launchIn(viewModelScope)

        nomisScoreInteractor.observeCurrentAccountScore()
            .onEach { score ->
                toolbarState.update { prevState ->
                    val newWalletIconState =
                        (prevState.homeIconState as? ToolbarHomeIconState.Wallet)?.copy(score = score?.score)
                    newWalletIconState?.let {
                        prevState.copy(homeIconState = newWalletIconState)
                    } ?: prevState
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeNetworkIssues() {
        combine(
            currentAssetsFlow,
            interactor.networkIssuesFlow(),
            selectedChainId
        ) { currentAssets, networkIssues, selectedChainId ->
            if (selectedChainId == null) return@combine null

            val isAllAssetsWithProblems =
                currentAssets.isNotEmpty() && currentAssets.filter { it.asset.token.configuration.chainId == selectedChainId }
                    .all {
                        it.asset.freeInPlanks == null || it.asset.freeInPlanks.lessThan(
                            BigInteger.ZERO
                        )
                    }
            if (isAllAssetsWithProblems.not()) return@combine null

            val selectedChainIssue = networkIssues[selectedChainId] ?: NetworkIssueType.Network

            WalletAssetsState.NetworkIssue(
                selectedChainId,
                selectedChainIssue,
                false
            )
        }.onEach { newState ->
            networkIssueStateFlow.update { newState }
        }.launchIn(viewModelScope)
    }

    // we open screen - no assets in the list
    private suspend fun buildInitialAssetsList(): AssetsLoadingState {
        return withContext(coroutineManager.default) {
            val currentMetaAccount = accountInteractor.selectedLightMetaAccount()
            val assets = chainInteractor.getChainAssets()

            val shimmers = when {
                currentMetaAccount.supportedEcosystems() == setOf(WalletEcosystem.Ton) -> {
                    val tonAsset = assets.firstOrNull { it.chainId == tonMainnetChainId || it.chainName.contains("ton", true) || it.name?.contains("ton", true) == true} ?: assets.first()
                    listOf(
                        AssetListItemShimmerViewState(
                            assetIconUrl = tonAsset.iconUrl,
                            assetChainUrls = listOf(tonAsset.chainIcon ?: tonAsset.iconUrl)
                        )
                    )
                }
                else -> {
                    assets
                        .asSequence()
                        .sortedWith(defaultChainAssetListSort())
                        .take(10)
                        .map { chainAsset ->
                            chainAsset.chainId to AssetListItemShimmerViewState(
                                assetIconUrl = chainAsset.iconUrl,
                                assetChainUrls = listOf(chainAsset.chainIcon ?: chainAsset.iconUrl)
                            )
                        }
                        .filter { selectedChainId.value == null || selectedChainId.value == it.first }
                        .map { it.second }
                        .toList()
                }
            }

            AssetsLoadingState.Loading(shimmers)
        }
    }

    private fun defaultChainAssetListSort() = compareBy<Asset> { it.isTestNet }
        .thenBy { it.chainId.defaultChainSort() }
        .thenBy { it.chainName }

    val state = MutableStateFlow(WalletState.default)

    private fun subscribeScreenState() {
        assetTypeState.onEach {
            state.value = state.value.copy(assetsState = it)
        }.launchIn(viewModelScope)

        assetTypeSelectorState.onEach {
            state.value = state.value.copy(multiToggleButtonState = it)
        }.launchIn(viewModelScope)

        currentWalletRecoveryStateFlow.distinctUntilChanged().onEach { (metaAccount, isRecoveryRequired) ->
            val showCurrenciesOrNftSelector =
                metaAccount.supportedEcosystems().contains(WalletEcosystem.Ethereum) || metaAccount.supportedEcosystems()
                    .contains(WalletEcosystem.Substrate)

            state.value = state.value.copy(
                isBackedUp = metaAccount.isBackedUp,
                isRecoveryRequired = isRecoveryRequired,
                scrollToTopEvent = Event(Unit),
                showCurrenciesOrNftSelector = showCurrenciesOrNftSelector
            )


            val customChainId = forcedPortfolioChainId(
                isPendulumMode = pendulumPreInstalledAccountsScenario.isPendulumMode(metaAccount.id),
                supportedEcosystems = metaAccount.supportedEcosystems()
            )

            if(customChainId != null) {
                interactor.saveChainId(metaAccount.id, customChainId)
            }
            selectedChainId.value= customChainId ?: interactor.getSavedChainId(metaAccount.id)
        }.launchIn(viewModelScope)

        showNetworkIssues.onEach {
            state.value = state.value.copy(hasNetworkIssues = it)
        }.launchIn(viewModelScope)
        subscribeTotalBalance()

        if (interactor.getAssetManagementIntroPassed().not()) {
            startManageAssetsIntroAnimation()
        }

        accountInteractor.lightMetaAccountsFlow().map { wallets ->
            val hasTonAccounts = wallets.any { it.tonPublicKey != null }
            val hasSubAccounts = wallets.any { it.substratePublicKey != null }
            val hasEthAccounts = wallets.any { it.ethereumPublicKey != null }
            hasTonAccounts to (hasSubAccounts || hasEthAccounts)
        }.distinctUntilChanged().onEach { (hasTon, hasSubOrEvm) ->
            state.value = state.value.copy(
                hasTonAccounts = hasTon,
                hasSubOrEvmAccounts = hasSubOrEvm
            )
        }.launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun startManageAssetsIntroAnimation() {
        awaitAssetsJob?.cancel()
        awaitAssetsJob = assetStates.filterIsInstance<AssetsLoadingState.Loaded>().filter { it.assets.isNotEmpty() }
            .map { it.assets.size }
            .distinctUntilChanged()
            .debounce(200L)
            .onEach {
                state.value = state.value.copy(scrollToBottomEvent = Event(Unit))
                interactor.saveAssetManagementIntroPassed()
                awaitAssetsJob?.cancel()
            }
            .catch {
                Log.d("BalanceListViewModel", it.message, it)
            }
            .launchIn(this)
        awaitAssetsJob?.start()
    }

    private fun subscribeTotalBalance() {
        capturedPortfolioHeaderFlow(
            balances = getTotalBalance.observe(),
            selectedChainIds = selectedChainId
        ).catch { failure ->
            if (failure is CancellationException) throw failure
            emit(CapturedPortfolioHeader(TotalBalance.Unavailable, null, null))
        }.onEach { header ->
            val presentation = presentTotalBalance(header.source, includeHidden = false)
            val balance = AssetBalanceViewState(
                transferableBalance = presentation.value,
                address = header.address.orEmpty(),
                changeViewState = presentation.change,
                balanceCaption = portfolioBalanceCaption(presentation, resourceManager)
            )
            state.update { it.copy(balance = balance) }
        }.launchIn(viewModelScope)
    }

    val toolbarState: MutableStateFlow<MainToolbarViewStateWithFilters> =
        MutableStateFlow(MainToolbarViewStateWithFilters(title = null, selectorViewState = null))

    init {
        if (updateSecurityNotice.isPending) {
            _showUpdateSecurityNotice.value = Event(Unit)
        }
        subscribeScreenState()
        observeToolbarStates()
        observeNetworkIssues()
        observeFiatSymbolChange()
        viewModelScope.launch {
            withContext(coroutineManager.io) {
            }
        }
//        sync()

        router.chainSelectorPayloadFlow.map { chainId ->
            val walletId = interactor.getSelectedMetaAccount().id
            interactor.saveChainId(walletId, chainId)

            selectedChainId.value = chainId
        }.launchIn(this)

        selectedChainId.onEach { chainId ->
            BalanceUpdateTrigger.invoke(chainId = chainId)
        }.launchIn(this)

    }

    /** It counts as shown only once closed; one that was not closed is shown at the next launch. */
    fun onUpdateSecurityNoticeClosed() = updateSecurityNotice.acknowledge()

    override fun onRefresh() {
        sync()
        viewModelScope.launch {
            BalanceUpdateTrigger.invoke()
        }
    }

    override fun onPortfolioLensChanged(lens: PortfolioLens) {
        currentPortfolio()?.walletId?.let { portfolioPreferences.setLens(it, lens) }
    }
    override fun onPortfolioGroupToggle(id: String) { currentPortfolio()?.walletId?.let { portfolioPreferences.toggleGroup(it, id) } }
    override fun onPortfolioNetworkToggle(id: String) { currentPortfolio()?.walletId?.let { portfolioPreferences.toggleNetwork(it, id) } }
    override fun onPortfolioZeroToggle(id: String?) {
        val loaded = currentPortfolio() ?: return
        val walletId = loaded.walletId ?: return
        if (id == null) portfolioPreferences.setZeroSummaryExpanded(walletId, !loaded.portfolioUi.zeroSummaryExpanded)
        else portfolioPreferences.toggleZeroGroup(walletId, id)
    }
    override fun onPortfolioValueNoticeDismiss() = portfolioPreferences.acknowledgeValueNotice()
    private fun currentPortfolio() = (state.value.assetsState as? WalletAssetsState.Assets)?.assets as? AssetsLoadingState.Loaded

    override fun onPortfolioMemberAction(walletId: Long, chainId: String, assetId: String, action: ActionItemType) {
        launch {
            val loaded = currentPortfolio() ?: return@launch
            if (loaded.walletId != walletId || interactor.getSelectedMetaAccount().id != walletId) return@launch
            val member = loaded.availableAssets.firstOrNull { it.state.chainId == chainId && it.state.chainAssetId == assetId } ?: return@launch
            if (member.state.metadataTrust != "verified" || member.state.isTestnet) return@launch
            when (action) {
                ActionItemType.SEND -> sendClicked(AssetPayload(chainId, assetId), walletId)
                ActionItemType.RECEIVE -> receiveClicked(AssetPayload(chainId, assetId))
                ActionItemType.HIDE -> hideAsset(chainId, assetId, walletId)
                else -> Unit
            }
        }
    }

    override fun onPortfolioHideGroup(walletId: Long, groupId: String) {
        launch {
            val loaded = currentPortfolio() ?: return@launch
            if (loaded.walletId != walletId) return@launch
            val members = loaded.availableAssets.filter { !it.state.isHidden && !it.state.isTestnet &&
                it.state.metadataTrust == "verified" && loaded.tokenGroups?.groupOf(it.state.chainId, it.state.chainAssetId)?.id == groupId }
            interactor.updateAssetsHiddenState(members.map { jp.co.soramitsu.common.model.AssetBooleanState(it.state.chainId, it.state.chainAssetId, false) }, walletId)
            portfolioPreferences.markHiddenFundedAssetsReviewed(walletId, members.filter { it.total?.signum() == 1 }
                .mapTo(mutableSetOf()) { PortfolioPresentationPreferences.assetKey(it.state.chainId, it.state.chainAssetId) })
        }
    }

    override fun onManageAssetClick() {
        router.openManageAssets()
    }

    // Manage assets lists hidden assets that hold funds first, with their amounts.
    override fun onHiddenFundsReviewClick() {
        router.openManageAssets("Hidden")
    }

    override fun onHiddenFundsKeepHiddenClick() {
        val loaded = (state.value.assetsState as? WalletAssetsState.Assets)?.assets as? AssetsLoadingState.Loaded
        val hiddenFundedAssetKeys = loaded?.exclusions?.hiddenFundedAssetKeys.orEmpty()
        launch {
            val walletId = loaded?.walletId ?: return@launch
            if (interactor.getSelectedMetaAccount().id == walletId) portfolioPreferences.dismissHiddenFundedNotice(walletId, hiddenFundedAssetKeys)
        }
    }

    override fun onRetry() {
        val (chainId, issueType, _) = networkIssueStateFlow.value ?: return

        if (issueType != NetworkIssueType.Account) {
            viewModelScope.launch {
                networkIssueStateFlow.update { it?.copy(retryButtonLoading = true) }
                interactor.retryChainSync(chainId)
                networkIssueStateFlow.update { it?.copy(retryButtonLoading = false) }
            }
        }
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
        val warnings =
            withContext(coroutineManager.default) { interactor.checkControllerDeprecations() }
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
            withContext(coroutineManager.default) {
//                getAvailableFiatCurrencies.sync()
                interactor.syncAssetsRates().onFailure {
                    withContext(coroutineManager.main) {
                        selectedFiat.notifySyncFailed()
                    }
                }
            }
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

            ActionItemType.HIDE -> {
                launch { hideAsset(chainId, chainAssetId) }
            }

            ActionItemType.SHOW -> {
                launch { showAsset(chainId, chainAssetId) }
            }

            else -> {}
        }
    }

    private suspend fun hideAsset(chainId: ChainId, chainAssetId: String, expectedWalletId: Long? = null) {
        // Hiding a funded asset here is the user's own decision; the notice must not question it.
        val isFunded = currentAssetsFlow.value.any { (asset, _, _) ->
            asset.token.configuration.chainId == chainId &&
                asset.token.configuration.id == chainAssetId &&
                asset.total.orZero() > BigDecimal.ZERO
        }
        if (isFunded) {
            portfolioPreferences.markHiddenFundedAssetsReviewed(
                metaId = expectedWalletId ?: interactor.getSelectedMetaAccount().id,
                assetKeys = setOf(PortfolioPresentationPreferences.assetKey(chainId, chainAssetId))
            )
        }
        if (expectedWalletId == null) interactor.markAssetAsHidden(chainId, chainAssetId)
        else interactor.updateAssetsHiddenState(listOf(jp.co.soramitsu.common.model.AssetBooleanState(chainId, chainAssetId, false)), expectedWalletId)
    }

    private suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload, expectedWalletId: Long? = null) {
        launch {
            if (expectedWalletId != null && interactor.getSelectedMetaAccount().id != expectedWalletId) return@launch
            // The transfer service enforces the veto again, so a failed lookup may proceed.
            val chain = runCatching { interactor.getChain(assetPayload.chainId) }.getOrNull()
            if (chain != null && sendPausePolicy.isPaused(chain)) {
                showSendPausedSheet(
                    resourceManager = resourceManager,
                    error = sendPausePolicy.pausedError(chain),
                    onReceive = {
                        if (expectedWalletId == null) receiveClicked(assetPayload)
                        else onPortfolioMemberAction(expectedWalletId, assetPayload.chainId, assetPayload.chainAssetId, ActionItemType.RECEIVE)
                    }
                )
            } else {
                if (expectedWalletId != null && interactor.getSelectedMetaAccount().id != expectedWalletId) return@launch
                router.openSend(assetPayload)
            }
        }
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
            // Portfolio rows are canonical, network-scoped assets. Never route through the
            // legacy ticker-group detail because it can combine unrelated same-symbol assets.
            val payload = AssetPayload(
                chainId = state.chainId,
                chainAssetId = state.chainAssetId
            )
            router.openAssetDetails(payload)
        }
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return currentMetaAccountFlow.map {
            addressIconGenerator.createAddressModel(it.supportedEcosystemWithIconAddress(), CURRENT_ICON_SIZE, it.name)
        }
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

    override fun onWalletRecoveryClick() {
        router.openRecoveryCenter()
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

    override fun onJoinSubOrEvmClicked() {
        router.openCreateSubstrateOrEvmAccountScreen()
    }

    override fun onJoinSubOrEvmCloseClick() {
        state.update { prevState ->
            prevState.copy(hasSubOrEvmAccounts = true)
        }
    }

    override fun onJoinTonClicked() {
        router.openCreateTonAccountScreen()
    }

    override fun onJoinTonCloseClick() {
        state.update { prevState ->
            prevState.copy(hasTonAccounts = true)
        }
    }

    private fun considerWalletBackedUp() {
        launch {
            val meta = accountInteractor.selectedLightMetaAccount()
            accountInteractor.updateWalletBackedUp(meta.id)
        }
    }

    override fun onAddressClick() {
        state.value.balance.address.takeIf { it.isNotEmpty() }?.let(::copyToClipboard)
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
            when {
                content.startsWith(QR_PREFIX_WALLET_CONNECT) -> {
                    sendWalletConnectPair(pairingUri = content)
                }

                content.startsWith(QR_PREFIX_TON_CONNECT) -> {
                    try { readTonQrContent(qrContent = content) } catch (e: Exception){
                        showError(e)
                    }
                }

                else -> {
                    val soraFormat =
                        interactor.tryReadSoraFormat(content)
                    if (soraFormat != null) {
                        val amount =
                            soraFormat.amount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                        openSendSoraTokenTo(soraFormat.tokenId, soraFormat.address, amount)
                        return@launch
                    }
                    // todo check if it's OK to use the amount from QR code for evm chains/assets
                    val amount = null //interactor.tryReadAmountFromQrContent(content)

                    val tonKeeperFormat = interactor.extractTonAddress(content)
                    if(tonKeeperFormat != null) {
                        router.openSend(
                            assetPayload = null,
                            initialSendToAddress = tonKeeperFormat,
                            amount = amount
                        )
                        return@launch
                    }

                    val ethFormat = interactor.extractEthAddress(content)
                    if (ethFormat != null) {
                        router.openSend(
                            assetPayload = null,
                            initialSendToAddress = ethFormat,
                            amount = amount
                        )
                        return@launch
                    }

                    router.openSend(
                        assetPayload = null,
                        initialSendToAddress = content,
                        amount = amount
                    )
                }
            }
        }
    }

    private suspend fun readTonQrContent(qrContent: String) {
        val uri = kotlin.runCatching { Uri.parse(qrContent) }.getOrNull() ?: return
        val clientId = uri.getQueryParameter("id")
        if (!isValidClientId(clientId)) {
            throw TonConnectException.WrongClientId(clientId)
        }
        val request = ConnectRequest.parse(uri.getQueryParameter("r"))

        if (request.items.isEmpty()) {
            showError(resourceManager.getString(R.string.common_undefined_error_message))
        }

        val app = kotlin.runCatching {
            tonConnectInteractor.readManifest(request.manifestUrl)
        }.getOrElse {
            showError(it)
            return
        }

        val signedRequest = router.openTonConnectionAndWaitForResult(app, request.proofPayload)
        kotlin.runCatching { tonConnectInteractor.respondDappConnectRequest(clientId!!, request, signedRequest, app) }
            .onFailure { showError(it) }

    }

    private fun isValidClientId(clientId: String?): Boolean {
        return !clientId.isNullOrBlank() && clientId.length == 64
    }

    private fun sendWalletConnectPair(pairingUri: String) {
        walletConnectInteractor.pair(
            pairingUri = pairingUri,
            onError = { error ->
                viewModelScope.launch(coroutineManager.main.immediate) {
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

    fun onServiceButtonClick() {
        router.openServiceScreen()
    }

    fun onScoreClick() {
        viewModelScope.launch {
            val currentAccount = currentMetaAccountFlow.first()
            router.openScoreDetailsScreen(currentAccount.id)
        }
    }
}
