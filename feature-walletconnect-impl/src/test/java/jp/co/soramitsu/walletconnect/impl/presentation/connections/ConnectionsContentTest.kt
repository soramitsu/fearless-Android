package jp.co.soramitsu.walletconnect.impl.presentation.connections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
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
class ConnectionsContentTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun listDisplaysSessionsAndReturnsSelectedConnection() {
        val first = SessionItemState("1", "Swap", "https://swap.example", null)
        val second = first.copy(topic = "2", title = "Gallery", imageUrl = "")
        var selected: SessionItemState? = null
        var search = ""
        compose.setContent {
            FearlessAppTheme {
                ConnectionsContent(
                    ConnectionsScreenViewState(listOf(first, second)),
                    onSessionClick = { selected = it },
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
            FearlessAppTheme { ConnectionsContent(ConnectionsScreenViewState(emptyList(), "missing")) }
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
                ConnectionsContent(ConnectionsScreenViewState.default, onCreateNewConnection = { created++ })
            }
        }
        compose.onNodeWithText("Nothing found for your request").assertDoesNotExist()
        compose.onNodeWithText("Create new connection").performClick()
        compose.runOnIdle { assertEquals(1, created) }
    }
}
