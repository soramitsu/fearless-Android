package jp.co.soramitsu.tonconnect.impl.presentation.connections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.tonconnect.api.model.DappModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TonConnectionsContentTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun listDisplaysDappsAndReturnsSelectedConnection() {
        val first = DappModel("1", listOf("-239"), "Swap", "https://swap.example", null, null, null, 1)
        val second = first.copy(identifier = "2", name = "Gallery", icon = "")
        var selected: DappModel? = null
        var search = ""
        compose.setContent {
            FearlessAppTheme {
                TonConnectionsContent(
                    TonConnectionsScreenViewState(listOf(first, second)),
                    onDappClick = { selected = it },
                    onSearchInput = { search = it }
                )
            }
        }
        compose.onNodeWithText("Swap").assertIsDisplayed().performClick()
        assertSame(first, selected)
        compose.onNodeWithText("Gallery").assertIsDisplayed().performClick()
        assertSame(second, selected)
        compose.onNode(hasSetTextAction()).performTextReplacement("gallery")
        compose.runOnIdle { assertEquals("gallery", search) }
    }

    @Test
    fun failedSearchExplainsEmptyResults() {
        compose.setContent {
            FearlessAppTheme { TonConnectionsContent(TonConnectionsScreenViewState(emptyList(), "missing")) }
        }
        compose.onNodeWithText("Sumimasen!").assertIsDisplayed()
        compose.onNodeWithText("Nothing found for your request").assertIsDisplayed()
        compose.onNodeWithText("Create new connection").assertIsDisplayed()
    }

    @Test
    fun emptyConnectionsAllowCreatingANewConnection() {
        var created = 0
        compose.setContent {
            FearlessAppTheme {
                TonConnectionsContent(TonConnectionsScreenViewState.default, onCreateNewConnection = { created++ })
            }
        }
        compose.onNodeWithText("Nothing found for your request").assertDoesNotExist()
        compose.onNodeWithText("Create new connection").performClick()
        compose.runOnIdle { assertEquals(1, created) }
    }
}
