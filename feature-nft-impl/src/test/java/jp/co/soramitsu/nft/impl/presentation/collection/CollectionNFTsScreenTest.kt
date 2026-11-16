package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.models.ScreenModel
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class CollectionNFTsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val page = MutableStateFlow<LoadableListPage<NFTsScreenView>>(ScreenModel.Reloading)
    private var itemClicks = 0
    private var sendClicks = 0

    @Before
    fun showCollection() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        val scrolling = mock<PageScrollingCallback>()
        compose.setContent {
            FearlessAppTheme {
                NavHost(rememberNavController(), NFTNavGraphRoute.CollectionNFTsScreen.routeName) {
                    CollectionNFTsNavComposable(page, scrolling)
                }
            }
        }
    }

    @Test
    fun loadedGridDisplaysHeaderAndForwardsTokenAndSendClicks() {
        compose.runOnIdle { page.value = ready(header(), section(), item(ScreenLayout.Grid)) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Collection description"))
        compose.onNodeWithText("Collection description").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bird one"))
        compose.onNodeWithText("Bird one").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, itemClicks) }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Transfer bird"))
        compose.onNodeWithText("Transfer bird").performClick()
        compose.runOnIdle { assertEquals(1, sendClicks) }
    }

    @Test
    fun pageLoadingPreservesTokensAndEmptyNextPageDoesNotReplaceThem() {
        compose.runOnIdle { page.value = ready(section(), item(ScreenLayout.List)) }
        compose.onNodeWithText("Bird one").assertIsDisplayed()
        compose.runOnIdle { page.value = ScreenModel.NextPageLoading }
        compose.onNodeWithText("Bird one").assertIsDisplayed()
        compose.runOnIdle { page.value = ScreenModel.PreviousPageLoading }
        compose.onNodeWithText("Bird one").assertIsDisplayed()
        compose.runOnIdle { page.value = ready(NFTsScreenView.EmptyPlaceHolder) }
        compose.onNodeWithText("Bird one").assertIsDisplayed()
        compose.onNodeWithText("Sumimasen!").assertDoesNotExist()
    }

    @Test
    fun emptyInitialResultDisplaysEmptyCollectionMessage() {
        // Reloading an empty list represents a fresh collection with no previously loaded tokens.
        compose.runOnIdle {
            page.value = object : LoadableListPage.Reloading<NFTsScreenView> {
                override val views = emptyList<NFTsScreenView>()
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { page.value = ready(NFTsScreenView.EmptyPlaceHolder) }
        compose.onNodeWithText("Sumimasen!").assertIsDisplayed()
        compose.onNodeWithText("There aren't any NFTs yet. Buy or mint NFTs to see them here.").assertIsDisplayed()
    }

    private fun ready(vararg items: NFTsScreenView) = object : LoadableListPage.ReadyToRender<NFTsScreenView> {
        override val views = items.toList()
    }

    private fun header() = object : NFTsScreenView.ScreenHeader {
        override val key = "header"
        override val thumbnail = Loadable.ReadyToRender<ImageModel>(ImageModel.ResId(R.drawable.drawable_fearless_bird))
        override val description = Loadable.ReadyToRender<TextModel?>(TextModel.SimpleString("Collection description"))
    }

    private fun section() = object : NFTsScreenView.SectionHeader {
        override val key = "section"
        override val title = Loadable.ReadyToRender<TextModel>(TextModel.SimpleString("My birds"))
    }

    private fun item(layout: ScreenLayout) = object : NFTsScreenView.ItemModel.WithButtonDecorator {
        override val key = "token-1"
        override val screenLayout = layout
        override val thumbnail = Loadable.ReadyToRender<ImageModel>(ImageModel.ResId(R.drawable.drawable_fearless_bird))
        override val title = Loadable.ReadyToRender<TextModel>(TextModel.SimpleString("Bird one"))
        override val description = Loadable.ReadyToRender<TextModel?>(TextModel.SimpleString("First edition"))
        override val onItemClick: () -> Unit = { itemClicks++ }
        override val buttonText = TextModel.SimpleString("Transfer bird")
        override val buttonImage = ImageModel.ResId(R.drawable.ic_send_outlined)
        override val onButtonClick: () -> Unit = { sendClicks++ }
    }
}
