package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class SelectorWithBorderTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun selectorVariantsShowTitlesIconsAndSubtitles() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    SelectorWithBorder(state = SelectorState(title = "Remote icon", subTitle = "12 KSM", iconUrl = ""))
                    SelectorWithBorder(state = SelectorState(title = "Override icon", subTitle = "3 DOT", iconUrl = "", iconOverrideResId = R.drawable.ic_wallet))
                    SelectorWithBorder(state = SelectorState(title = "Sub icon", subTitle = "Alert", iconUrl = null, subTitleIcon = R.drawable.ic_alert_16))
                    SelectorWithBorder(state = SelectorState(title = "No action", subTitle = null, iconUrl = null, actionIcon = null))
                    SelectorWithBorder(state = SelectorState(title = "Disabled", subTitle = "0 GLMR", iconUrl = "", enabled = false))
                }
            }
        }

        listOf("Remote icon", "Override icon", "Sub icon", "No action", "Disabled").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        listOf("12 KSM", "3 DOT", "Alert", "0 GLMR").forEach {
            assertTrue(
                "subtitle $it is shown",
                compose.onAllNodesWithText(it, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            )
        }
        // Icons describe themselves with the selector title.
        compose.onAllNodesWithContentDescription("Remote icon").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Override icon").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Sub icon").assertCountEquals(0)
    }

    @Test
    fun clickableSelectorReportsTapsAndLockedOneDoesNot() {
        var open = 0
        var locked = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    SelectorWithBorder(state = SelectorState("Open", "x", null), onClick = { open++ })
                    SelectorWithBorder(state = SelectorState("Locked", "y", null, clickable = false), onClick = { locked++ })
                }
            }
        }

        compose.onNodeWithText("Open", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Locked", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(1, open)
        assertEquals(0, locked)
    }

    @Test
    fun defaultStateIsAnUntitledNetworkSelector() {
        val state = SelectorState.default

        assertEquals("Network", state.title)
        assertNull(state.subTitle)
        assertNull(state.iconUrl)
        assertEquals(R.drawable.ic_arrow_down, state.actionIcon)
        assertTrue(state.clickable && state.enabled)
    }
}
