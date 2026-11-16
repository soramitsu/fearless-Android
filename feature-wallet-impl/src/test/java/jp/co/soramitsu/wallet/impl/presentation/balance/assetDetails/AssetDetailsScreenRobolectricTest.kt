package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails

import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import org.junit.Before
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortCallback
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortContent
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortState
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.AssetDetailsSortViewState
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort.EmptyAssetDetailsSortViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class AssetDetailsScreenRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : AssetDetailsCallback {
        override fun onNavigationBack() { calls += "back" }
        override fun onSelectChainClick() { calls += "selectChain" }
        override fun onChainTabClick(tab: AssetDetailsState.Tab) { calls += "tab:$tab" }
        override fun onSortChainsClick() { calls += "sort" }
        override fun onChainClick(itemState: AssetDetailsState.ItemState) { calls += "chain:${itemState.chainId}" }
    }

    @Test
    fun contentListsNetworksAndReportsTabsSortingAndClicks() {
        compose.setContent {
            FearlessAppTheme {
                AssetDetailsContent(
                    state = AssetDetailsState(
                        assetSorting = AssetSorting.FiatBalance,
                        balanceState = LoadingState.Loaded(AssetBalanceViewState("$12.00", "", changeViewState = null)),
                        tabState = MultiToggleButtonState(AssetDetailsState.Tab.AvailableChains, AssetDetailsState.Tab.entries),
                        items = listOf(
                            item("polkadot", "Polkadot", "10 DOT", "$60"),
                            item("kusama", "Kusama", null, null),
                            item("loading", null, null, null),
                            item("broken", "Broken", "1 DOT", "$6", NetworkIssueType.Network)
                        )
                    ),
                    callback = callback
                )
            }
        }

        compose.onNodeWithText("$12.00").assertIsDisplayed()
        compose.onNodeWithText("10 DOT").assertIsDisplayed()
        compose.onNodeWithText("$60").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("1 DOT")).fetchSemanticsNodes().size)
        compose.onNodeWithText("Polkadot").performClick()
        compose.onNodeWithText(string(R.string.common_my_networks)).performClick()
        compose.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .let { assertTrue(it.size >= 5) }
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("chain:polkadot", "tab:MyChains")))
    }

    @Test
    fun loadingBalanceWithoutTabsRendersOnlyTheList() {
        compose.setContent {
            FearlessAppTheme {
                AssetDetailsContent(state = AssetDetailsState.empty.copy(items = listOf(item("a", "Astar", "2 ASTR", "$1"))), callback = callback)
            }
        }

        compose.onNodeWithText("Astar").performClick()
        assertEquals(0, compose.onAllNodes(hasText(string(R.string.common_my_networks))).fetchSemanticsNodes().size)
        assertEquals(listOf("chain:a"), calls)
    }

    @Test
    fun toolbarShowsTitleAndSelectedNetwork() {
        compose.setContent {
            FearlessAppTheme {
                AssetDetailsToolbar(
                    state = LoadingState.Loaded(
                        MainToolbarViewState(
                            title = "USDT",
                            homeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_arrow_back_24dp),
                            selectorViewState = ChainSelectorViewState(selectedChainName = "Moonbeam", selectedChainId = "m")
                        )
                    ),
                    callback = callback
                )
            }
        }

        compose.onNodeWithText("USDT").assertIsDisplayed()
        compose.onNodeWithText("Moonbeam").assertIsDisplayed()
    }

    @Test
    fun toolbarShimmerWhileLoading() {
        compose.setContent {
            FearlessAppTheme {
                AssetDetailsToolbar(state = LoadingState.Loading(), callback = callback)
            }
        }

        assertEquals(0, compose.onAllNodes(hasText("USDT")).fetchSemanticsNodes().size)
    }

    @Test
    fun sortSheetMarksTheSelectionAndReportsChoices() {
        val fiat = AssetDetailsSortState.Sorting(R.string.common_assets_filters_fiat)
        val name = AssetDetailsSortState.Sorting(R.string.common_assets_filters_name)
        var selected: AssetDetailsSortState.Sorting? = null
        var closed = false
        compose.setContent {
            FearlessAppTheme {
                AssetDetailsSortContent(
                    state = AssetDetailsSortViewState(ToolbarViewState("Sort by", R.drawable.ic_cross_24), listOf(fiat, name), fiat),
                    callback = object : AssetDetailsSortCallback {
                        override fun onNavigationClose() { closed = true }
                        override fun onSortingSelected(sorting: AssetDetailsSortState.Sorting) { selected = sorting }
                    }
                )
            }
        }

        compose.onNodeWithText("Sort by").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_assets_filters_name)).performClick()
        compose.waitForIdle()

        assertEquals(name, selected)
        assertEquals(false, closed)
        assertEquals(null, EmptyAssetDetailsSortViewState.selectedSorting)
        assertTrue(EmptyAssetDetailsSortViewState.items.isEmpty())
    }

    private fun item(
        chainId: String,
        chainName: String?,
        asset: String?,
        fiat: String?,
        issue: NetworkIssueType? = null
    ): AssetDetailsState.ItemState = if (chainName != null) {
        AssetDetailsItemViewState("usdt", chainId, "", chainName, asset, fiat, issue)
    } else object : AssetDetailsState.ItemState {
        override val assetId: String = "usdt"
        override val chainId: String = chainId
        override val iconUrl: String = ""
        override val chainName: String? = chainName
        override val assetRepresentation: String? = asset
        override val fiatRepresentation: String? = fiat
        override val networkIssueType: NetworkIssueType? = issue
    }
}
