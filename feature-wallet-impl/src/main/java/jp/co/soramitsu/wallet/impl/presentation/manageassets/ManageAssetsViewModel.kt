package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.isDetectedAsset
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class ManageAssetsViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val accountInteractor: AccountInteractor,
    private val chainInteractor: ChainInteractor,
    private val resourceManager: ResourceManager,
    private val portfolioVisibility: PortfolioVisibility,
    private val sendPausePolicy: SendPausePolicy,
    private val portfolioPreferences: PortfolioPresentationPreferences,
    private val tokenGroupRegistry: jp.co.soramitsu.runtime.multiNetwork.tokengroups.TokenGroupRegistry
) : BaseViewModel(), ManageAssetsContentInterface {

    /** Only user interactions belong here. An absent entry preserves the persisted Auto state. */
    private val pendingExplicitAssetStates = MutableStateFlow<List<AssetBooleanState>>(emptyList())

    private val allRows = MutableStateFlow<List<ManageAssetItemState>>(emptyList())
    private val visibilityFilter = MutableStateFlow(ManageAssetFilter.All)
    private val networkFilter = MutableStateFlow(ChainSelectorViewStateWithFilters.Filter.All)
    private var chainSnapshot: List<Chain> = emptyList()
    private var boundWalletId: Long? = null
    private var initialFilterApplied = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val manageSectionsFlow = walletInteractor.selectedMetaAccountFlow().map { it.id }.distinctUntilChanged()
        .flatMapLatest { id -> combine(portfolioPreferences.observeManageSections(id), portfolioPreferences.observeLens(id)) { sections, lens ->
            Triple(id, sections, lens)
        } }

    private val selectedChainIdFlow = MutableStateFlow<ChainId?>(null)

    private val savedChainFlow = selectedChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val enteredTokenQueryFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val showTestnetsFlow = walletInteractor.selectedMetaAccountFlow()
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeShowTestnets)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val legacyHiddenAssetsFlow = walletInteractor.selectedMetaAccountFlow()
        .map { it.id }
        .distinctUntilChanged()
        .flatMapLatest(portfolioPreferences::observeLegacyHiddenAssets)

    val state = MutableStateFlow(ManageAssetsScreenViewState.default)

    private fun subscribeScreenState() {
        combine(
            savedChainFlow,
            walletInteractor.observeSelectedAccountChainSelectFilter()
        ) { chain, filterApplied ->

            networkFilter.value = filterApplied
            val selectedChainTitle = chain?.name ?: when (filterApplied) {
                ChainSelectorViewStateWithFilters.Filter.All ->
                    resourceManager.getString(R.string.chain_selection_all_networks)

                ChainSelectorViewStateWithFilters.Filter.Popular ->
                    resourceManager.getString(R.string.network_management_popular)

                ChainSelectorViewStateWithFilters.Filter.Favorite ->
                    resourceManager.getString(R.string.network_managment_favourite)
            }
            state.value = state.value.copy(selectedChainTitle = selectedChainTitle)
        }.launchIn(this)

        jp.co.soramitsu.common.utils.combine(allRows, tokenGroupRegistry.observeResolvedGroups(),
            visibilityFilter, manageSectionsFlow, enteredTokenQueryFlow, networkFilter) { rows, groups, filter, sections, query, networkFilter ->
            if (sections.first != boundWalletId) {
                state.update { it.copy(assets = null, allSectionAssets = emptyMap()) }
                return@combine
            }
            val lens = sections.third
            fun sectionKey(item: ManageAssetItemState): String =
                if (selectedChainIdFlow.value != null && filter != ManageAssetFilter.Hidden) item.canonicalAssetKey
                else if (lens == jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByNetwork) "network:${item.chainId}"
                else groups.groupOf(item.chainId, item.id)?.let { "group:${it.id}" } ?: item.canonicalAssetKey
            val visible = rows.filter { item ->
                val inNetworkScope = if (filter == ManageAssetFilter.Hidden) true
                    else if (selectedChainIdFlow.value != null) item.chainId == selectedChainIdFlow.value
                    else when (networkFilter) {
                        ChainSelectorViewStateWithFilters.Filter.All -> true
                        ChainSelectorViewStateWithFilters.Filter.Favorite -> item.isFavorite
                        ChainSelectorViewStateWithFilters.Filter.Popular -> item.isRankedMainnet
                    }
                inNetworkScope && filter.accepts(item) && (query.isBlank() || item.symbol.contains(query, true) ||
                    item.assetName.orEmpty().contains(query, true) || item.chainName.contains(query, true))
            }
            state.update { it.copy(assets = visible.groupBy(::sectionKey), allSectionAssets = rows.groupBy(::sectionKey),
                visibilityFilter = filter, collapsedSections = sections.second, lens = lens) }
        }.launchIn(this)

        jp.co.soramitsu.common.utils.combine(
            walletInteractor.assetsFlowAndAccount(),
            chainInteractor.getChainsFlow(),
            walletInteractor.selectedMetaAccountFlow(),
            walletInteractor.observeSelectedAccountChainSelectFilter(),
            selectedChainIdFlow,
            enteredTokenQueryFlow,
            pendingExplicitAssetStates,
            showTestnetsFlow,
            legacyHiddenAssetsFlow
        ) { (assetWalletId, assets), chains, currentMetaAccount, filter, selectedChainId, searchQuery, pendingStates, showTestnets, legacyHiddenAssets ->
            if (assetWalletId != currentMetaAccount.id) {
                boundWalletId = currentMetaAccount.id
                pendingExplicitAssetStates.value = emptyList()
                allRows.value = emptyList()
                return@combine null
            }

            val changedWallet = boundWalletId != currentMetaAccount.id
            if (changedWallet) {
                boundWalletId = currentMetaAccount.id
                pendingExplicitAssetStates.value = emptyList()
            }
            chainSnapshot = chains
            val selectedAccountFavoriteChains = currentMetaAccount.favoriteChains
            val chainsWithFavoriteInfo = chains.map { chain ->
                chain to (selectedAccountFavoriteChains[chain.id]?.isFavorite == true)
            }
            val filteredChains = chains.filter {
                val accountId = currentMetaAccount.accountId(it)
                accountId != null && accountId.contentEquals(emptyAccountIdValue).not() // has account
            }.filter {
                // Testnets live behind the Testnets toggle, unless the user picked one directly.
                showTestnets || it.id == selectedChainId || !portfolioVisibility.isTestnet(it)
            }

            val allChainAssets = filteredChains.map { it.assets }.flatten()

            val filteredChainAssets = allChainAssets

            filteredChainAssets.map { chainAsset ->
                val asset = assets.find { chainAsset.id == it.asset.token.configuration.id && chainAsset.chainId == it.asset.token.configuration.chainId }
                chainAsset to asset
            }.sortedWith(compareByDescending<Pair<Asset, AssetWithStatus?>> {
                // Hidden assets that hold funds come first: they are what the total leaves out.
                it.second?.asset.isHiddenWithBalance()
            }.thenBy {
                it.second == null
            }.thenBy {
                it.second?.asset?.enabled == false
            }.thenByDescending {
                it.second?.asset?.fiatAmount.orZero()
            }.thenByDescending {
                it.second?.asset?.total.orZero()
            }.thenBy {
                it.second?.asset?.token?.configuration?.chainName
            }).map {
                val (chainAsset, assetWithStatus) = it
                // The same amount as the portfolio row: the total, free + reserved.
                val total = assetWithStatus?.asset?.total
                val chain = filteredChains.firstOrNull { it.id == chainAsset.chainId }
                    ?: error("Filtered asset ${chainAsset.id} has no owning chain ${chainAsset.chainId}")
                val fiatAmount = assetWithStatus?.asset?.fiatAmount
                    ?.takeIf { !portfolioVisibility.isTestnet(chain) && portfolioVisibility.hasTrustedPrice(chainAsset) &&
                        !chainAsset.isDetectedAsset() }
                    ?.formatFiat(assetWithStatus.asset.token.fiatSymbol)
                val pendingPreference = pendingStates.takeUnless { changedWallet }.orEmpty().find { state ->
                    state.assetId == chainAsset.id && state.chainId == chainAsset.chainId
                }?.value
                // An Auto row is checked exactly when the portfolio shows it.
                val isChecked = pendingPreference ?: assetWithStatus?.asset?.enabled
                    ?: portfolioVisibility.isVisible(
                        enabled = null,
                        total = total,
                        chain = chain,
                        asset = chainAsset,
                        isFavourite = selectedAccountFavoriteChains[chainAsset.chainId]?.isFavorite == true,
                        isOnlySelectedNetwork = chain.id == selectedChainId,
                        isSendVetoed = sendPausePolicy.isPaused(chain),
                        showTestnets = showTestnets
                    )

                val canonicalAssetKey = canonicalManageAssetKey(chain, chainAsset)
                ManageAssetItemState(
                    canonicalAssetKey = canonicalAssetKey,
                    id = chainAsset.id,
                    imageUrl = chainAsset.iconUrl,
                    chainName = chainAsset.chainName,
                    assetName = chainAsset.name,
                    symbol = chainAsset.symbol.uppercase(),
                    amount = total?.formatCrypto() ?: resourceManager.getString(R.string.portfolio_balance_unavailable),
                    fiatAmount = fiatAmount,
                    chainId = chainAsset.chainId,
                    isChecked = isChecked,
                    preference = when (pendingPreference ?: assetWithStatus?.asset?.enabled) {
                        true -> "shown"
                        false -> "hidden"
                        null -> "auto"
                    },
                    isZeroAmount = total?.isZero() == true,
                    showEdit = false,
                    isTestnet = portfolioVisibility.isTestnet(chain),
                    isFavorite = selectedAccountFavoriteChains[chain.id]?.isFavorite == true,
                    isRankedMainnet = chain.rank != null && !portfolioVisibility.isTestnet(chain),
                    hiddenNote = hiddenNote(
                        isHiddenWithBalance = assetWithStatus?.asset.isHiddenWithBalance(),
                        isLegacyHide = PortfolioPresentationPreferences.assetKey(chainAsset.chainId, chainAsset.id) in legacyHiddenAssets
                    )
                )
            }.let { rows -> Triple(rows, searchQuery, showTestnets) }
        }.filterNotNull().onEach { (rows, searchQuery, showTestnets) ->
            state.value = state.value.copy(
                searchQuery = searchQuery,
                showTestnets = showTestnets
            )
            allRows.value = rows
        }.launchIn(this)
    }

    init {
        subscribeScreenState()

        viewModelScope.launch {
            val metaId = accountInteractor.selectedLightMetaAccount().id
            selectedChainIdFlow.value = walletInteractor.getSavedChainId(metaId)
        }

        walletRouter.chainSelectorPayloadFlow.map { chainId ->
            val walletId = accountInteractor.selectedLightMetaAccount().id
            walletInteractor.saveChainId(walletId, chainId)
            selectedChainIdFlow.value = chainId
        }.launchIn(this)
    }

    fun applyInitialFilter(raw: String?) {
        if (initialFilterApplied) return
        initialFilterApplied = true
        onFilterChanged(ManageAssetFilter.entries.firstOrNull { it.name == raw } ?: ManageAssetFilter.All)
    }

    override fun onFilterChanged(filter: ManageAssetFilter) { visibilityFilter.value = filter }
    override fun onSectionToggle(id: String) { boundWalletId?.let { portfolioPreferences.toggleManageSection(it, id) } }
    override fun onGroupChecked(section: String, checked: Boolean) {
        val groups = tokenGroupRegistry.resolve(chainSnapshot)
        val members = allRows.value.filter { item ->
            if (section.startsWith("group:")) groups.groupOf(item.chainId, item.id)?.id == section.removePrefix("group:")
            else section == "network:${item.chainId}"
        }
        val updates = members.map { AssetBooleanState(it.chainId, it.id, checked) }
        val keys = updates.mapTo(mutableSetOf()) { it.chainId to it.assetId }
        pendingExplicitAssetStates.update { old -> old.filterNot { (it.chainId to it.assetId) in keys } + updates }
    }

    override fun onSearchInput(input: String) {
        enteredTokenQueryFlow.value = input
    }

    override fun onChecked(assetItemState: ManageAssetItemState, checked: Boolean) {
        pendingExplicitAssetStates.update { previous ->
            previous.filterNot {
                it.assetId == assetItemState.id && it.chainId == assetItemState.chainId
            } + AssetBooleanState(
                chainId = assetItemState.chainId,
                assetId = assetItemState.id,
                value = checked
            )
        }
    }

    override fun onShowTestnetsChanged(show: Boolean) {
        launch {
            portfolioPreferences.setShowTestnets(accountInteractor.selectedLightMetaAccount().id, show)
        }
    }

    override fun onItemClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onEditClicked(assetItemState: ManageAssetItemState) {
    }

    override fun onDoneClicked() {
        walletRouter.back()
    }

    override fun onSelectedChainClicked() {
        launch {
            val selectedChain = savedChainFlow.firstOrNull()
            val selectedChainId = selectedChain?.id
            if(selectedChain?.ecosystem == Ecosystem.Ton) {
                return@launch
            }
            walletRouter.openSelectChain(selectedChainId, isFilteringEnabled = true)
        }
    }

    fun onDialogClose() {
        walletRouter.setChainSelectorPayload(selectedChainIdFlow.value)
        val walletId = boundWalletId ?: return
        val changes = pendingExplicitAssetStates.value
        pendingExplicitAssetStates.value = emptyList()
        if (changes.isNotEmpty()) {
            // Switching a funded asset off here is the user's own decision; the portfolio must not
            // announce it as a hidden asset that holds funds.
            val fundedAssetKeys = allRows.value
                .filterNot(ManageAssetItemState::isZeroAmount)
                .mapTo(mutableSetOf()) { PortfolioPresentationPreferences.assetKey(it.chainId, it.id) }
            val hiddenFundedAssetKeys = changes.filterNot(AssetBooleanState::value)
                .mapTo(mutableSetOf()) { PortfolioPresentationPreferences.assetKey(it.chainId, it.assetId) }
                .intersect(fundedAssetKeys)
            kotlinx.coroutines.MainScope().launch {
                portfolioPreferences.markHiddenFundedAssetsReviewed(
                    metaId = walletId,
                    assetKeys = hiddenFundedAssetKeys
                )
                walletInteractor.updateAssetsHiddenState(changes, walletId)
            }
        }
    }

    // A hide kept from an earlier version is never presented as the user's recent choice.
    private fun hiddenNote(isHiddenWithBalance: Boolean, isLegacyHide: Boolean): String? = when {
        !isHiddenWithBalance -> null
        isLegacyHide -> resourceManager.getString(R.string.portfolio_hidden_in_previous_version)
        else -> resourceManager.getString(R.string.portfolio_asset_hidden)
    }
}

private fun WalletAsset?.isHiddenWithBalance(): Boolean {
    return this != null && enabled == false && total.orZero() > BigDecimal.ZERO
}

internal fun canonicalManageAssetKey(chain: Chain, asset: Asset): String =
    chain.assetKey(asset).serialized

internal fun groupManageAssetsByCanonicalIdentity(
    assets: List<ManageAssetItemState>
): Map<String, List<ManageAssetItemState>> = assets.groupBy(ManageAssetItemState::canonicalAssetKey)
