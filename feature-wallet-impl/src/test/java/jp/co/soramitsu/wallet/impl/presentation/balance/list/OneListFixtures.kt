package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroup
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.assetRow

/**
 * A wallet with test values only (no keys, no real address): DOT on Polkadot, USDT on two networks
 * (one approved group), GLMR on Moonbeam and KSM at zero on Kusama. The list is built by the same
 * [PortfolioDiscovery] and [PortfolioListBuilder] the view model uses.
 */
internal object OneListFixtures {

    private val dot = holding("dot", "Polkadot", "Polkadot", "polkadot", "dot", total = "12.5", fiat = "75.00")
    private val usdtMoonbeam = holding("usdt", "Tether", "Moonbeam", "moonbeam", "usdt-m", total = "40", fiat = "40.00")
    private val usdtHub = holding("usdt", "Tether", "Polkadot Asset Hub", "assethub", "usdt-a", total = "25", fiat = "25.00")
    private val glmr = holding("glmr", "Moonbeam", "Moonbeam", "moonbeam", "glmr", total = "120", fiat = "18.40")
    private val ksm = holding("ksm", "Kusama", "Kusama", "kusama", "ksm", total = "0", fiat = "0.00")

    private val holdings = listOf(dot, usdtMoonbeam, usdtHub, glmr, ksm)

    val groups = ResolvedTokenGroups(
        listOf(
            ResolvedTokenGroup(
                id = "usdt", symbol = "USDT", name = "Tether", precision = 6,
                members = listOf(ResolvedTokenGroupMember("moonbeam", "usdt-m"), ResolvedTokenGroupMember("assethub", "usdt-a"))
            )
        )
    )

    @Suppress("LongParameterList")
    fun home(
        searchQuery: String = "",
        selectedNetwork: String? = null,
        networkGroup: ChainSelectorViewStateWithFilters.Filter? = null,
        networkFilterAvailable: Boolean = true,
        showOneListNotice: Boolean = false,
        expandedGroups: Set<String> = emptySet()
    ): WalletState {
        val visible = holdings.filter { (selectedNetwork == null || it.state.chainId == selectedNetwork) && PortfolioDiscovery.matches(it, searchQuery) }
        val ui = PortfolioUiState(valueNoticeAcknowledged = true, showOneListNotice = showOneListNotice, expandedGroups = expandedGroups)
        val loaded = AssetsLoadingState.Loaded(
            assets = visible.map { it.state },
            walletHasPositiveBalance = true,
            walletId = 7L,
            selectedNetwork = selectedNetwork,
            portfolioAssets = visible,
            availableAssets = holdings,
            portfolioItems = PortfolioListBuilder.build(visible, emptyList(), groups, ui, selectedNetwork, true,
                searching = searchQuery.isNotBlank()),
            portfolioUi = ui,
            tokenGroups = groups,
            searchQuery = searchQuery,
            networkOptions = PortfolioDiscovery.networks(holdings, null),
            networkGroup = networkGroup,
            networkFilterAvailable = networkFilterAvailable
        )
        return state(loaded)
    }

    fun noMatch(selectedNetwork: String?): WalletState = home(searchQuery = "btc", selectedNetwork = selectedNetwork)

    /** The same holdings without a wallet's one list: one section per network, each under its header. */
    fun networkSections(): WalletState = state(AssetsLoadingState.Loaded(holdings.map { it.state }))

    fun state(loaded: AssetsLoadingState.Loaded) = WalletState.default.copy(
        assetsState = WalletAssetsState.Assets(loaded, isHideVisible = true),
        balance = AssetBalanceViewState("$158.40", "test-address-not-a-key", true, ChangeBalanceViewState("+1.2%", "+$1.88")),
        isBackedUp = true,
        showCurrenciesOrNftSelector = true
    )

    @Suppress("LongParameterList")
    private fun holding(symbol: String, name: String, chainName: String, chainId: String, assetId: String, total: String, fiat: String) =
        PortfolioAsset(
            assetRow(symbol = symbol, name = name, chainName = chainName, chainId = chainId, chainAssetId = assetId,
                balance = total, fiat = "$$fiat").copy(priceTrust = "canonical"),
            total = BigDecimal(total),
            trustedFiat = BigDecimal(fiat),
            fiatSymbol = "$"
        )
}

/**
 * Scrolls the chip row so the chip is centred (or as near as the row's ends allow). The row runs under the page gutter,
 * so `performScrollTo` would ask the list, which cannot scroll sideways, to bring the gutter part into view as well.
 */
internal fun ComposeContentTestRule.revealChip(tag: String) {
    val chip = onNodeWithTag(tag).getUnclippedBoundsInRoot()
    val row = onNodeWithTag(FILTERS_TAG).getUnclippedBoundsInRoot()
    val dx = with(density) { ((chip.left + chip.right) / 2 - (row.left + row.right) / 2).toPx() }
    onNodeWithTag(FILTERS_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(dx, 0f) }
    waitForIdle()
}
