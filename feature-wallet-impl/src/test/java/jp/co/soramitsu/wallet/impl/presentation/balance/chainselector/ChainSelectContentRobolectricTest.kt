package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters.Filter
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** The network picker in both palettes: the sun-lit rows and the legacy rows share state, callbacks and test tags. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class ChainSelectContentRobolectricTest(private val sunlit: Boolean) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val contract = object : ChainSelectScreenContract {
        override fun onBackButtonClick() { calls += "back" }
        override fun onFilterApplied(filter: Filter) { calls += "filter:$filter" }
        override fun onChainMarkedFavorite(chainItemState: ChainSelectScreenContract.State.ItemState) { calls += "favorite:${chainItemState.id}" }
        override fun onChainSelected(chainItemState: ChainSelectScreenContract.State.ItemState?) { calls += "select:${chainItemState?.id}" }
        override fun onSearchInput(input: String) { calls += "search:$input" }
        override fun onDialogClose() { calls += "close" }
    }

    @Before
    fun setUp() {
        if (!sunlit) {
            mockkObject(FwRestyle)
            every { FwRestyle.isEnabled(any()) } returns false
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun filteredListReportsSelectionFavoritesFiltersAndSearch() {
        render(
            ChainSelectScreenContract.State.Impl.FilteringDecorator(
                appliedFilter = Filter.Favorite,
                selectedFilter = Filter.Favorite,
                state = ChainSelectScreenContract.State.Impl(
                    chains = listOf(
                        ChainSelectScreenContract.State.ItemState.Impl("1", null, "Kusama", balance = "1 KSM").toFilteredDecorator(false),
                        ChainSelectScreenContract.State.ItemState.Impl("2", "https://x/y.svg", "Moonriver").toFilteredDecorator(true)
                    ),
                    selectedChainId = "1",
                    onlyNetworkName = "Polkadot"
                )
            )
        )

        compose.onNodeWithText(string(R.string.common_network_management)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_only_network, "Polkadot")).assertIsDisplayed()
        compose.onNodeWithText("1 KSM").assertIsDisplayed()
        compose.onNodeWithTag("ChainItem_image_selected", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("ChainFilter_Popular").performClick()
        compose.onNodeWithText("Moonriver").performClick()
        compose.onAllNodesWithTag("ChainItem_marked_favourite", useUnmergedTree = true)[0].performClick()
        compose.onNodeWithTag("ChainItem_image_all_networks", useUnmergedTree = true).onParent().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("kus")
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("filter:Popular", "select:2", "favorite:1", "select:null", "search:kus")))
    }

    @Test
    fun allNetworksRowIsMarkedWhenNothingIsSelected() {
        render(
            ChainSelectScreenContract.State.Impl(
                chains = listOf(ChainSelectScreenContract.State.ItemState.Impl("1", null, "Kusama")),
                selectedChainId = null
            )
        )

        compose.onNodeWithText(string(R.string.chain_selection_all_networks)).assertIsDisplayed()
        compose.onNodeWithTag("ChainItem_image_selected", useUnmergedTree = true).assertExists()
        assertEquals(0, compose.onAllNodesWithTag("ChainFilter_All").fetchSemanticsNodes().size)
    }

    @Test
    fun popularFilterTitlesTheAllRow() {
        render(
            ChainSelectScreenContract.State.Impl.FilteringDecorator(
                appliedFilter = Filter.All,
                selectedFilter = Filter.Popular,
                state = ChainSelectScreenContract.State.Impl(
                    chains = listOf(ChainSelectScreenContract.State.ItemState.Impl("1", null, "Kusama")),
                    selectedChainId = null
                )
            )
        )

        assertEquals(if (sunlit) 2 else 1, compose.onAllNodes(hasText(string(R.string.network_management_popular)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("ChainItem_image_selected", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun emptyResultExplainsNothingMatched() {
        render(ChainSelectScreenContract.State.Impl(chains = emptyList(), selectedChainId = null, showAllChains = false))

        compose.onNodeWithText(string(jp.co.soramitsu.common.R.string.common_search_network_and_assets_alert_description))
            .assertIsDisplayed()
    }

    @Test
    fun loadingShowsNoRows() {
        render(ChainSelectScreenContract.State.Impl.default)

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.chain_selection_all_networks))).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.common_network_management)).assertIsDisplayed()
    }

    @Test
    fun itemStatesMarkSelectionThroughDecorators() {
        val item = ChainSelectScreenContract.State.ItemState.Impl("1", null, "Kusama")
        val decorated = item.toFilteredDecorator(true)

        assertTrue(item.markSelected(true).isSelected)
        assertTrue(decorated.markSelected(true).isSelected)
        assertTrue((decorated.markSelected(true) as ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator).isMarkedAsFavorite)
        assertEquals("Kusama", decorated.title)
    }

    private fun render(state: ChainSelectScreenContract.State) {
        compose.setContent {
            FearlessAppTheme {
                ChainSelectContent(state = state, contract = contract)
            }
        }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "sunlit={0}")
        fun palettes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
