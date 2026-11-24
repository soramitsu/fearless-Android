package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import org.junit.Before
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.assetRow
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2000dp")
class SearchAssetsScreenRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : SearchAssetsScreenInterface {
        override fun onAssetSearchEntered(value: String) { calls += "search:$value" }
        override fun backClicked() { calls += "back" }
        override fun actionItemClicked(actionType: ActionItemType, chainId: String, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
            calls += "action:$actionType:$chainAssetId"
        }
        override fun assetClicked(state: AssetListItemViewState) { calls += "asset:${state.chainAssetId}" }
    }

    @Test
    fun resultsAreSplitIntoPortfolioHiddenAndOtherSections() {
        render(
            SearchAssetState(
                assets = AssetsLoadingState.Loaded(listOf(assetRow(symbol = "dot", chainAssetId = "dot"))),
                searchQuery = "o",
                hiddenAssets = listOf(assetRow(symbol = "ksm", chainAssetId = "ksm", isHidden = true)),
                otherAssets = listOf(assetRow(symbol = "glmr", chainAssetId = "glmr"))
            )
        )

        compose.onNodeWithTag("SearchAssetsSection_portfolio").assertIsDisplayed()
        compose.onNodeWithTag("SearchAssetsSection_hidden").assertIsDisplayed()
        compose.onNodeWithTag("SearchAssetsSection_other").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.search_assets_section_other_description)).assertIsDisplayed()
        compose.onNodeWithText("GLMR").performClick()
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("x")
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("asset:glmr", "back")))
        assertTrue(calls.any { it.startsWith("search:") })
    }

    @Test
    fun noMatchesShowsTheEmptyMessage() {
        render(SearchAssetState(assets = AssetsLoadingState.Loaded(emptyList()), searchQuery = "zzz"))

        compose.onNodeWithText(string(R.string.common_search_network_and_assets_alert_description)).assertIsDisplayed()
        assertEquals(false, SearchAssetState(assets = AssetsLoadingState.Loaded(emptyList())).hasResults)
    }

    @Test
    fun loadingShowsShimmerRows() {
        render(SearchAssetState(assets = AssetsLoadingState.Loading(listOf(AssetListItemShimmerViewState("", listOf(""))))))

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.common_search_network_and_assets_alert_description))).fetchSemanticsNodes().size)
    }

    @Test
    fun loadingWithoutShimmerStatesShowsTheDefaultShimmer() {
        render(SearchAssetState())

        compose.onNodeWithText(string(R.string.common_cancel)).assertIsDisplayed()
    }

    private fun render(state: SearchAssetState) {
        compose.setContent {
            FearlessAppTheme {
                SearchAssetsScreen(data = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }
}
