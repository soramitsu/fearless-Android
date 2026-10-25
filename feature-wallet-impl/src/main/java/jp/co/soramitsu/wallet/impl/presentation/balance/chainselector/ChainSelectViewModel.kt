package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.multiNetwork.chain.model.defaultChainSort
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

@HiltViewModel
class ChainSelectViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val chainInteractor: ChainInteractor,
    savedStateHandle: SavedStateHandle,
    private val sharedSendState: SendSharedState,
    private val accountInteractor: AccountInteractor,
    private val portfolioVisibility: PortfolioVisibility,
    private val tokenGroupRegistry: jp.co.soramitsu.runtime.multiNetwork.tokengroups.TokenGroupRegistry,
) : BaseViewModel(), ChainSelectScreenContract {

    private val initialSelectedChainId: ChainId? = savedStateHandle[ChainSelectFragment.KEY_SELECTED_CHAIN_ID]
    private val selectedChainId = MutableStateFlow(initialSelectedChainId)

    private val initialSelectedAssetId: String? = savedStateHandle[ChainSelectFragment.KEY_SELECTED_ASSET_ID]
    private val filterChainIds: List<ChainId>? = savedStateHandle[ChainSelectFragment.KEY_FILTER_CHAIN_IDS]
    private val chooserMode: Boolean = savedStateHandle[ChainSelectFragment.KEY_CHOOSER_MODE] ?: false
    private val showAllChains: Boolean = savedStateHandle[ChainSelectFragment.KEY_SHOW_ALL_CHAINS] ?: true
    private val tokenCurrencyId: String? = savedStateHandle[ChainSelectFragment.KEY_CURRENCY_ID]
    private val isSelectAsset: Boolean = savedStateHandle[ChainSelectFragment.KEY_SELECT_ASSET] ?: true
    private val isFilteringEnabled: Boolean = savedStateHandle[ChainSelectFragment.KEY_FILTERING_ENABLED] ?: false

    // XCM
    private val xcmChainType: XcmChainType? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_CHAIN_TYPE]
    private val xcmSelectedOriginChainId: String? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_SELECTED_ORIGIN_CHAIN_ID]
    private val xcmOriginAssetId: String? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_ORIGIN_ASSET_ID]
    private val xcmAssetSymbol: String? =
        savedStateHandle[ChainSelectFragment.KEY_XCM_ASSET_SYMBOL]

    private var choiceDone = false
    private var selectionWalletId: Long? = null
    private var exactCandidates = emptyList<jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember>()

    private val cachedMetaAccountFlow = accountInteractor.selectedMetaAccountFlow()
        .stateIn(this, SharingStarted.Eagerly, null)

    private val allChainsFlow = if (xcmChainType == null) {
        chainInteractor.getChainsFlow()
    } else {
        combine(
            chainInteractor.getChainsFlow(),
            chainInteractor.getXcmChainIdsFlow(
                type = xcmChainType,
                originChainId = xcmSelectedOriginChainId,
                originAssetId = xcmOriginAssetId,
                assetSymbol = xcmAssetSymbol
            )
        ) { chains, xcmChainIds ->
            chains.filter {
                it.id in xcmChainIds
            }
        }
    }.flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val chainsFlow = combine(
        cachedMetaAccountFlow.mapNotNull { it },
        allChainsFlow
    ) { meta, chains ->
        if (selectionWalletId == null) selectionWalletId = meta.id
        if (selectionWalletId != meta.id) return@combine emptyList()
        val accountNetworks = chains.filter { chain -> meta.accountId(chain)?.let {
            !it.contentEquals(emptyAccountIdValue)
        } == true }.mapTo(mutableSetOf()) { it.id }
        exactCandidates = if (initialSelectedAssetId != null && initialSelectedChainId != null && xcmChainType == null) {
            ExactNetworkAssetSwitch.candidates(initialSelectedChainId, initialSelectedAssetId,
                tokenGroupRegistry.resolve(chains), accountNetworks)
        } else emptyList()
        val filteredChains = when {
            initialSelectedAssetId != null && xcmChainType == null -> {
                chains.filter { chain -> exactCandidates.any { it.chainId == chain.id } }
            }
            filterChainIds.isNullOrEmpty() -> {
                chains
            }
            else -> {
                chains.filter { it.id in filterChainIds }
            }
        }

        filteredChains.filter { it.id in accountNetworks }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private val enteredChainQueryFlow = MutableStateFlow("")

    private val filterFlow = MutableStateFlow<ChainSelectorViewStateWithFilters.Filter?>(null)

    private val favoriteItemVMCacheFlow =
        MutableStateFlow<ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator?>(null)

    private val favoriteItemsDBCacheFlow =
        accountInteractor.observeSelectedMetaAccountFavoriteChains().flowOn(Dispatchers.IO).share()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favoriteChainsCacheResolverFlow = flow {
        var favoritesSnapshot: MutableMap<ChainId, Boolean>? = null

        favoriteItemVMCacheFlow.transformLatest { itemState ->
            val vmCache = favoritesSnapshot?.apply {
                if (itemState != null)
                    put(itemState.id, !itemState.isMarkedAsFavorite)
            }

            if (vmCache != null) {
                emit(vmCache)
            }

            favoriteItemsDBCacheFlow.collectLatest { dbCache ->
                /*
                    Wait till user finishes all favorite chains selection
                    to compare dbCache and current vmCache

                    1) All delays will be cancelled by use of collectLatest except the last one

                    2) if itemState is null, user has not selected anything as of yet; so,
                    we consider this collection to be startup of this screen - thus, no delay needed
                */
                if (itemState != null)
                    kotlinx.coroutines.delay(10_000)

                if (dbCache == vmCache)
                    return@collectLatest

                favoritesSnapshot = dbCache.toMutableMap()
                emit(dbCache)
            }
        }.collect(this)
    }

    val state = combine(
        chainsFlow,
        walletInteractor.observeSelectedAccountChainSelectFilter(),
        filterFlow,
        selectedChainId,
        enteredChainQueryFlow,
        favoriteChainsCacheResolverFlow,
        walletInteractor.assetsFlowAndAccount()
    ) {
      chainsPreFiltered,
      savedFilter,
      userInputFilter,
      selectedChainId,
      searchQuery,
      favoriteChains,
      walletAssets ->

        val chainsWithFavoriteInfo = chainsPreFiltered?.map { chain ->
            chain to (favoriteChains[chain.id] ?: false)
        }

        val filterInUse = if (isFilteringEnabled) userInputFilter ?: savedFilter else ChainSelectorViewStateWithFilters.Filter.All

        val chainItems = when(filterInUse) {
            ChainSelectorViewStateWithFilters.Filter.All -> chainsWithFavoriteInfo

            ChainSelectorViewStateWithFilters.Filter.Favorite ->
                chainsWithFavoriteInfo?.filter { (_, isFavorite) -> isFavorite }

            ChainSelectorViewStateWithFilters.Filter.Popular ->
                chainsWithFavoriteInfo?.filter { (chain, _) ->
                    chain.rank != null && !portfolioVisibility.isTestnet(chain)
                }?.sortedBy { (chain, _) ->
                    chain.rank
                }
        }?.map { (chain, isFavorite) ->
            val memberId = ExactNetworkAssetSwitch.selected(exactCandidates, chain.id)
            val memberAsset = chain.assetsById[memberId]
            val amount = memberAsset?.let { asset ->
                if (walletAssets.first != selectionWalletId) null else walletAssets.second.firstOrNull {
                    it.asset.token.configuration.chainId == chain.id && it.asset.token.configuration.id == asset.id
                }?.asset?.total?.formatCrypto(asset.symbol)
            }
            chain.toChainItemState().copy(balance = amount).run {
                if (!isFilteringEnabled)
                    return@run this

                this.toFilteredDecorator(isFavorite)
            }
        }

        val resultingChains = chainItems
            ?.filter { searchQuery.isEmpty() || it.title.contains(searchQuery, true) }
            ?.apply {
                if (filterInUse != ChainSelectorViewStateWithFilters.Filter.Popular) {
                    sortedWith(
                        compareBy<ChainSelectScreenContract.State.ItemState> {
                            it.id.defaultChainSort()
                        }.thenBy {
                            it.title
                        }
                    )
                }
            }

        val shouldShowAllChains =
            initialSelectedAssetId == null && (!showAllChains || isFilteringEnabled && resultingChains?.isNotEmpty() != true).not()

        ChainSelectScreenContract.State.Impl(
            chains = resultingChains,
            selectedChainId = selectedChainId,
            searchQuery = searchQuery,
            showAllChains = shouldShowAllChains,
            onlyNetworkName = chainsPreFiltered?.singleOrNull()?.name.takeIf { initialSelectedAssetId != null }
        ).run {
            if (!isFilteringEnabled)
                return@run this

            ChainSelectScreenContract.State.Impl.FilteringDecorator(
                appliedFilter = savedFilter,
                selectedFilter = filterInUse,
                state = this
            )
        }
    }.stateIn(this, SharingStarted.Eagerly, ChainSelectScreenContract.State.Impl.default)

    override fun onBackButtonClick() {
        walletRouter.back()
    }

    override fun onChainSelected(chainItemState: ChainSelectScreenContract.State.ItemState?) {
        val chainId = chainItemState?.id
        if (chooserMode) {
            choiceDone = true
            saveAppliedFilter()
            selectedChainId.value = chainId
            walletRouter.setChainSelectorPayload(chainId)
            walletRouter.back()
            return
        }
        if (chainId == null) return
        launch {
            val meta = walletInteractor.getSelectedMetaAccount()
            if (selectionWalletId == null || meta.id != selectionWalletId) return@launch
            val chains = chainInteractor.getChainsFlow().first()
            if (walletInteractor.getSelectedMetaAccount().id != meta.id) return@launch
            val chain = chains.filter { it.id == chainId }.singleOrNull() ?: return@launch
            val networks = chains.filter { candidate -> meta.accountId(candidate)?.let { !it.contentEquals(emptyAccountIdValue) } == true }
                .mapTo(mutableSetOf()) { it.id }
            if (chainId !in networks) return@launch
            if (!isSelectAsset) {
                assetSpecified(assetId = null, chainId = chainId)
                return@launch
            }
            if (initialSelectedAssetId != null && xcmChainType == null) {
                val origin = initialSelectedChainId ?: return@launch
                val candidates = ExactNetworkAssetSwitch.candidates(origin, initialSelectedAssetId, tokenGroupRegistry.resolve(chains), networks)
                val exactAssetId = ExactNetworkAssetSwitch.selected(candidates, chainId) ?: return@launch
                if (chain.assetsById[exactAssetId] == null) return@launch
                assetSpecified(assetId = exactAssetId, chainId = chainId)
            } else if (chain.assets.size == 1) {
                assetSpecified(assetId = chain.assets.single().id, chainId = chain.id)
            } else {
                choiceDone = true
                walletRouter.back()
                walletRouter.openSelectChainAsset(chain.id)
            }
        }
    }

    private fun saveAppliedFilter() {
        val walletId = cachedMetaAccountFlow.value?.id ?: return

        launch {
            walletInteractor.saveChainSelectFilter(
                walletId,
                filterFlow.replayCache.firstOrNull()?.toString().orEmpty()
            )
        }
    }

    private fun assetSpecified(assetId: String?, chainId: ChainId) {
        choiceDone = true
        if (assetId != null) {
            sharedSendState.update(assetId = assetId, chainId = chainId)
        }
        walletRouter.backWithResult(
            WalletRouterApi.KEY_CHAIN_ID to chainId,
            WalletRouterApi.KEY_ASSET_ID to assetId
        )
    }

    override fun onFilterApplied(filter: ChainSelectorViewStateWithFilters.Filter) {
        filterFlow.value = filter
    }

    override fun onChainMarkedFavorite(chainItemState: ChainSelectScreenContract.State.ItemState) {
        if (chainItemState !is ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator)
            return

        favoriteItemVMCacheFlow.tryEmit(chainItemState)

        launch(Dispatchers.IO) {
            val metaId = requireNotNull(cachedMetaAccountFlow.value).id
            accountInteractor.updateFavoriteChain(
                metaId = metaId,
                chainId = chainItemState.id,
                isFavorite = !chainItemState.isMarkedAsFavorite
            )
        }
    }

    override fun onSearchInput(input: String) {
        enteredChainQueryFlow.value = input
    }

    override fun onDialogClose() {
        if (!choiceDone && sharedSendState.assetId == null) {
            walletRouter.popOutOfSend()
        }
    }
}
