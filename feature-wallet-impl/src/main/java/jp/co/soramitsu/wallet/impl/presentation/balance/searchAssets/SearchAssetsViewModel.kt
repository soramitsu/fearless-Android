package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.AssetSearchSection
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.AssetListHelper
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Search over the wallet's assets, decided by the portfolio's own visibility rule
 * ([PortfolioVisibility.searchSection]): the rows the portfolio shows, then explicitly hidden
 * assets, then (for a query) reviewed registry assets the wallet does not hold yet, so a token can
 * be found before the first deposit. A result opens the exact (network, asset) detail, like a
 * portfolio row.
 */
@HiltViewModel
class SearchAssetsViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val interactor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    private val assetMetadataDescriptorStore: AssetMetadataDescriptorStore,
    private val router: WalletRouter,
    private val portfolioVisibility: PortfolioVisibility,
    private val sendPausePolicy: SendPausePolicy,
    private val portfolioPreferences: PortfolioPresentationPreferences,
    private val featureToggleStore: ProductFeatureToggleStore
) : BaseViewModel(), SearchAssetsScreenInterface {

    private val _showUnsupportedChainAlert = MutableLiveData<Event<Unit>>()
    val showUnsupportedChainAlert: LiveData<Event<Unit>> = _showUnsupportedChainAlert

    private val _openPlayMarket = MutableLiveData<Event<Unit>>()
    val openPlayMarket: LiveData<Event<Unit>> = _openPlayMarket

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val selectedWalletIdFlow = interactor.selectedMetaAccountFlow()
        .map { it.id }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val showTestnetsFlow = selectedWalletIdFlow
        .flatMapLatest(portfolioPreferences::observeShowTestnets)

    // The network the portfolio list is limited to, if any: its primary utility is listed at zero.
    private val selectedNetworkFlow = selectedWalletIdFlow
        .map { walletId -> runCatching { interactor.getSavedChainId(walletId) }.getOrNull() }

    private val searchCatalogFlow = combine(
        interactor.assetsFlowAndAccount(),
        chainInteractor.getChainsFlow(),
        interactor.selectedMetaAccountFlow(),
        selectedNetworkFlow,
        showTestnetsFlow
    ) { (walletId: Long, assets: List<AssetWithStatus>), chains: List<Chain>, metaAccount, selectedChainId: ChainId?, showTestnets: Boolean ->
        if (metaAccount.id != walletId) return@combine null

        val chainsById = chains.associateBy(Chain::id)
        val favouriteChains = metaAccount.favoriteChains

        val assetsBySection = assets
            .groupBy { (asset, _, _) ->
                val chainId = asset.token.configuration.chainId
                val chain = chainsById[chainId] ?: return@groupBy null

                portfolioVisibility.searchSection(
                    enabled = asset.enabled,
                    total = asset.total,
                    chain = chain,
                    asset = asset.token.configuration,
                    isFavourite = favouriteChains[chainId]?.isFavorite == true,
                    isOnlySelectedNetwork = chainId == selectedChainId,
                    isSendVetoed = sendPausePolicy.isPaused(chain),
                    showTestnets = showTestnets
                )
            }

        fun section(section: AssetSearchSection): List<BalanceListItemModel> = AssetListHelper.processAssets(
            assets = assetsBySection[section].orEmpty(),
            filteredChains = chains,
            metadataDescriptor = assetMetadataDescriptorStore::get,
            isTestnet = portfolioVisibility::isTestnet,
            hasTrustedPrice = portfolioVisibility::hasTrustedPrice
        )

        SearchCatalog(
            portfolio = AssetListHelper.sortByNetwork(
                AssetListHelper.filterForPortfolioPresentation(
                    assets = section(AssetSearchSection.Portfolio),
                    assetDiscoveryShadowEnabled = featureToggleStore.assetDiscoveryShadowEnabled
                )
            ),
            hidden = section(AssetSearchSection.Hidden).sortedWith(
                compareByDescending<BalanceListItemModel> { it.total?.signum() == 1 }
                    .thenByDescending { it.fiatAmount.orZero() }
                    .thenBy { it.chain?.rank ?: Int.MAX_VALUE }
                    .thenBy { it.asset.symbol.lowercase() }
            ),
            other = section(AssetSearchSection.Other)
                .filter { it.metadataTrust == AssetMetadataTrust.Verified }
        )
    }

    private fun buildInitialAssetsList(): List<AssetListItemShimmerViewState> {
        return List(SHIMMER_ITEMS_COUNT) { index ->
            AssetListItemShimmerViewState(
                assetIconUrl = "asset_$index",
                assetChainUrls = List(2) { "chain_${index}_$it" }
            )
        }
    }

    val state = combine(
        searchCatalogFlow,
        enteredAssetQueryFlow
    ) { catalog: SearchCatalog?, searchQuery: String ->
        if (catalog == null) {
            return@combine SearchAssetState(
                assets = AssetsLoadingState.Loading(buildInitialAssetsList()),
                searchQuery = searchQuery
            )
        }

        val query = searchQuery.trim()
        val portfolio = catalog.portfolio.filter { it.matches(query) }
        // An empty query lists the portfolio only, never every registry asset.
        val hidden = if (query.isEmpty()) emptyList() else catalog.hidden.filter { it.matches(query) }
        val other = if (query.isEmpty()) {
            emptyList()
        } else {
            catalog.other
                .filter { it.matches(query) }
                .sortedWith(
                    compareByDescending<BalanceListItemModel> { it.asset.symbol.equals(query, ignoreCase = true) }
                        .thenBy { it.chain?.rank ?: Int.MAX_VALUE }
                        .thenBy { it.chain?.name.orEmpty() }
                        .thenBy { it.asset.symbol.lowercase() }
                )
                .take(MAX_OTHER_ASSETS)
        }

        SearchAssetState(
            assets = AssetsLoadingState.Loaded(portfolio.mapIndexed { index, item -> item.toAssetState(index) }),
            hiddenAssets = hidden.mapIndexed { index, item -> item.toAssetState(index) },
            otherAssets = other.mapIndexed { index, item -> item.toAssetState(index) },
            searchQuery = searchQuery
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = SearchAssetState(assets = AssetsLoadingState.Loading(buildInitialAssetsList()))
    )

    @OptIn(ExperimentalMaterialApi::class)
    override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
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

    suspend fun hideAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsHidden(chainId, chainAssetId)
    }

    suspend fun showAsset(chainId: ChainId, chainAssetId: String) {
        interactor.markAssetAsShown(chainId, chainAssetId)
    }

    private fun sendClicked(assetPayload: AssetPayload) {
        router.openSend(assetPayload)
    }

    private fun receiveClicked(assetPayload: AssetPayload) {
        router.openReceive(assetPayload)
    }

    override fun backClicked() {
        router.back()
    }

    override fun assetClicked(state: AssetListItemViewState) {
        if (state.isSupported.not()) {
            _showUnsupportedChainAlert.value = Event(Unit)
            return
        }

        // A result is one exact (network, asset), like a portfolio row: open its detail directly.
        router.openAssetDetails(
            AssetPayload(
                chainId = state.chainId,
                chainAssetId = state.chainAssetId
            )
        )
    }

    fun updateAppClicked() {
        _openPlayMarket.value = Event(Unit)
    }

    override fun onAssetSearchEntered(query: String) {
        enteredAssetQueryFlow.value = query
    }

    private fun BalanceListItemModel.matches(query: String): Boolean {
        return query.isEmpty() ||
            asset.symbol.contains(query, ignoreCase = true) ||
            asset.name.orEmpty().contains(query, ignoreCase = true) ||
            chain?.name.orEmpty().contains(query, ignoreCase = true)
    }

    private class SearchCatalog(
        val portfolio: List<BalanceListItemModel>,
        val hidden: List<BalanceListItemModel>,
        val other: List<BalanceListItemModel>
    )

    companion object {
        private const val SHIMMER_ITEMS_COUNT = 8

        /** A short query matches hundreds of registry assets; the closest ones are enough. */
        const val MAX_OTHER_ASSETS = 50
    }
}
