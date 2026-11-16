package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverDappScreenTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<DiscoverDappScreenInterface>()
    private val app = dapp("swap", "Bird Swap", "Exchange tokens")

    @Test
    fun connectedEmptyListShowsExplanationAndAllowsDiscoverSelection() {
        val state = DiscoverDappState(emptyList(), MultiToggleButtonState(DappListType.Connected, DappListType.entries))
        compose.setContent { FearlessAppTheme { DiscoverDappScreen(state, callback) } }
        compose.onNodeWithText("No connected dApps").assertIsDisplayed()
        compose.onNodeWithText("Discover dApp").performClick()
        verify(callback).onButtonToggleChanged(DappListType.Discover)
    }

    @Test
    fun discoveryShowsGroupsAndForwardsSeeAllAndDappSelection() {
        val state = DiscoverDappState.default.copy(dapps = listOf(DappConfig("DeFi", listOf(app))))
        compose.setContent { FearlessAppTheme { DiscoverDappScreen(state, callback) } }
        compose.onNodeWithText("DeFi").assertIsDisplayed()
        compose.onNodeWithText("Exchange tokens").assertIsDisplayed()
        compose.onNodeWithText("SEE ALL").performClick()
        verify(callback).onSeeAllClick("DeFi")
        compose.onNodeWithText("Bird Swap").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        verify(callback).onDappClick(app)
    }

    @Test
    fun featuredBannerDisplaysItsDescriptionAndSelectsItsDapp() {
        val featured = dapp("featured", "Featured birds", "Featured description").copy(background = "", icon = "")
        val state = DiscoverDappState.default.copy(
            dapps = listOf(DappConfig("top", listOf(featured, app)), DappConfig("DeFi", listOf(app)))
        )
        compose.setContent { FearlessAppTheme { DiscoverDappScreen(state, callback) } }
        compose.onNodeWithText("Featured description").assertIsDisplayed()
        compose.onNodeWithText("Featured birds").performClick()
        verify(callback).onDappClick(featured)
        compose.onNodeWithText("DeFi").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun groupWithoutHeaderSupportsLongPress() {
        var selected: DappModel? = null
        compose.setContent {
            FearlessAppTheme {
                DappsGroup(DappConfig(null, listOf(app)), null, null, { selected = it })
            }
        }
        compose.onNodeWithText("SEE ALL").assertDoesNotExist()
        compose.onNodeWithText("Bird Swap").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(app, selected)
    }

    @Test
    fun seeAllSearchMatchesNameUrlAndDescriptionAndShowsNoResults() {
        val second = dapp("second", "Gallery", "Digital art").copy(url = "https://museum.example")
        compose.setContent {
            FearlessAppTheme {
                SeeAllDappsBottomSheet(DappsListState("Browse dApps", listOf(app, second)), {}, {})
            }
        }
        compose.onNodeWithText("Browse dApps").assertIsDisplayed()
        compose.onNodeWithText("Bird Swap").assertIsDisplayed()
        for (query in listOf("GALLERY", "MUSEUM", "digital")) {
            compose.onNode(hasSetTextAction()).performTextReplacement(query)
            compose.onNodeWithText("Gallery").assertIsDisplayed()
            compose.onNodeWithText("Bird Swap").assertDoesNotExist()
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("unknown")
        compose.onNodeWithText("No connected dApps").assertIsDisplayed()
        compose.onNodeWithText("Gallery").assertDoesNotExist()
    }

    @Test
    fun seeAllSelectionReturnsTheOriginalDapp() {
        var selected: DappModel? = null
        compose.setContent {
            FearlessAppTheme {
                SeeAllDappsBottomSheet(DappsListState("Browse", listOf(app)), { selected = it }, {})
            }
        }
        compose.onNodeWithText("Bird Swap").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(app, selected)
    }

    private fun dapp(
        id: String,
        name: String,
        description: String
    ) = DappModel(
        id, listOf("-239"), name, "https://swap.example", description, null, null, null
    )
}
