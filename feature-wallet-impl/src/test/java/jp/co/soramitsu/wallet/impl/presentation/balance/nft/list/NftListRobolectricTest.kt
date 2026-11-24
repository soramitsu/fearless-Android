package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenModel
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenView
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2000dp")
class NftListRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val scrolling = object : PageScrollingCallback {
        override fun onAllPrevPagesScrolled() { calls += "prev" }
        override fun onAllNextPagesScrolled() { calls += "next" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun readyPageShowsNetworksCollectionsAndPlaceholders() {
        render(
            ready(
                header("Moonbeam"),
                item("punks", "Punks", "Moonbeam", ScreenLayout.Grid, quantity = "1/10"),
                item("apes", "Apes", "Moonbeam", ScreenLayout.List, quantity = null),
                loadingItem(),
                NFTCollectionsScreenView.EmptyPlaceholder
            ),
            filtersApplied = true
        )

        assertTrue(compose.onAllNodes(hasText("Moonbeam", ignoreCase = true)).fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Punks").assertIsDisplayed()
        compose.onNodeWithText("1/10").assertIsDisplayed()
        compose.onNodeWithText("Apes").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.nft_stub_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.nft_list_empty_message)).assertIsDisplayed()
        compose.onNodeWithText("Punks").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.contains("click:punks"))
    }

    @Test
    fun settingsBarReportsLayoutAndFilterClicks() {
        render(ready(header("Astar")), filtersApplied = false)

        val clickable = compose.onAllNodes(hasClickAction())
        clickable[0].performClick()
        clickable[1].performClick()
        clickable[2].performClick()
        compose.waitForIdle()

        assertEquals(listOf("layout:Grid", "layout:List", "filters"), calls)
    }

    @Test
    fun pagingStatesInsertAndReplaceTheLoadingIndication() {
        val pages = MutableStateFlow(
            model(ready(header("Astar"), item("a", "Alpha", "Astar", ScreenLayout.Grid, "1/1")), filtersApplied = false)
        )
        compose.setContent {
            val current by pages.collectAsState()
            FearlessAppTheme { NFTScreen(current) }
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()

        compose.runOnIdle {
            pages.value = model(object : LoadableListPage.NextPageLoading<NFTCollectionsScreenView> {}, filtersApplied = false)
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()

        compose.runOnIdle {
            pages.value = model(object : LoadableListPage.PreviousPageLoading<NFTCollectionsScreenView> {}, filtersApplied = false)
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()

        compose.runOnIdle {
            pages.value = model(object : LoadableListPage.Reloading<NFTCollectionsScreenView> {
                override val views: Collection<NFTCollectionsScreenView> = listOf(header("Reloading"), loadingItem(), loadingItem("second"))
            }, filtersApplied = false)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Reloading", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reloading", ignoreCase = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Alpha")).fetchSemanticsNodes().size)
    }

    private fun render(page: LoadableListPage<NFTCollectionsScreenView>, filtersApplied: Boolean) {
        compose.setContent {
            FearlessAppTheme {
                NFTScreen(model(page, filtersApplied))
            }
        }
        compose.waitForIdle()
    }

    private fun model(page: LoadableListPage<NFTCollectionsScreenView>, filtersApplied: Boolean) = NFTCollectionsScreenModel(
        areFiltersApplied = filtersApplied,
        screenLayout = ScreenLayout.Grid,
        loadableListPage = page,
        onFiltersIconClick = { calls += "filters" },
        onScreenLayoutChanged = { calls += "layout:$it" },
        pageScrollingCallback = scrolling
    )

    private fun ready(vararg views: NFTCollectionsScreenView) = object : LoadableListPage.ReadyToRender<NFTCollectionsScreenView> {
        override val views: Collection<NFTCollectionsScreenView> = views.toList()
    }

    private fun header(name: String) = object : NFTCollectionsScreenView.NetworkHeader {
        override val key: Any = "header:$name"
        override val networkName: String = name
    }

    private fun loadingItem(key: String = "loading") = object : NFTCollectionsScreenView.ItemModel {
        override val key: Any = key
        override val screenLayout: ScreenLayout = ScreenLayout.Grid
        override val thumbnail: Loadable<ImageModel> = Loadable.InProgress()
        override val chainName: Loadable<TextModel> = Loadable.InProgress()
        override val title: Loadable<TextModel> = Loadable.InProgress()
        override val onItemClick: () -> Unit = {}
    }

    private fun item(key: String, title: String, chain: String, layout: ScreenLayout, quantity: String?): NFTCollectionsScreenView.ItemModel {
        val base = object : NFTCollectionsScreenView.ItemModel {
            override val key: Any = key
            override val screenLayout: ScreenLayout = layout
            override val thumbnail: Loadable<ImageModel> = Loadable.ReadyToRender(ImageModel.ResId(R.drawable.drawable_fearless_bird))
            override val chainName: Loadable<TextModel> = Loadable.ReadyToRender(TextModel.SimpleString(chain))
            override val title: Loadable<TextModel> = Loadable.ReadyToRender(TextModel.SimpleString(title))
            override val onItemClick: () -> Unit = { calls += "click:$key" }
        }
        return if (quantity == null) base else object : NFTCollectionsScreenView.ItemModel.WithQuantityDecorator,
            NFTCollectionsScreenView.ItemModel by base {
            override val contentType: Any get() = base.contentType
            override val quantity: TextModel = TextModel.SimpleString(quantity)
        }
    }
}
