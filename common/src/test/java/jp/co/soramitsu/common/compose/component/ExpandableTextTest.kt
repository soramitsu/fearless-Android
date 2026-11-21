package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Duration
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ExpandableTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun expandableTextTogglesItsContentAndReportsEachTap() {
        var taps = 0
        compose.show {
            ExpandableText(title = "Details", onClick = { taps++ }) { Text("Hidden details") }
        }

        compose.onNodeWithText("Hidden details").assertDoesNotExist()
        compose.onNodeWithText("Details").tap()
        compose.onNodeWithText("Hidden details").assertIsDisplayed()
        compose.onNodeWithText("Details").tap()
        compose.onNodeWithText("Hidden details").assertDoesNotExist()

        assertEquals(2, taps)
    }

    @Test
    fun headerOnlyClickKeepsTheStateButStillReports() {
        var taps = 0
        compose.show {
            Column {
                ExpandableText(title = "Open", initialState = true, isFullWidthClickable = false, onClick = { taps++ }) { Text("Open body") }
                AdvancedExpandableText(title = "Advanced", initialState = true, isFullWidthClickable = false, onClick = { taps++ }) {
                    Text("Advanced body")
                }
            }
        }

        compose.onNodeWithText("Open").tap()
        compose.onNodeWithText("Advanced").tap()

        compose.onNodeWithText("Open body").assertIsDisplayed()
        compose.onNodeWithText("Advanced body").assertIsDisplayed()
        assertEquals(2, taps)
    }

    @Test
    fun advancedExpandableTextSwapsItsIconWithTheContent() {
        var taps = 0
        compose.show {
            AdvancedExpandableText(title = "Fees", onClick = { taps++ }) { Text("Fee table") }
        }

        compose.onNodeWithText("Fee table").assertDoesNotExist()
        compose.onNodeWithText("Fees").tap()
        compose.onNodeWithText("Fee table").assertIsDisplayed()
        assertEquals(1, taps)
    }

    @Test
    fun collapsibleViewReportsTheNewCollapsedState() {
        val states = mutableListOf<Boolean>()
        compose.show {
            CollapsibleView(title = "Permissions", onActionClick = { states += it }) { Text("Sign transactions") }
        }

        compose.onNodeWithText("Sign transactions").assertDoesNotExist()
        compose.onNodeWithText("Permissions").tap()
        compose.onNodeWithText("Sign transactions").assertIsDisplayed()
        compose.onNodeWithText("Permissions").tap()
        compose.onNodeWithText("Sign transactions").assertDoesNotExist()

        assertEquals(listOf(false, true), states)
    }

    @Test
    fun collapsibleViewPreviewStartsExpanded() {
        compose.show { CollapsibleViewPreview() }

        compose.onNodeWithText("Review required permissions").assertIsDisplayed()
        compose.onNodeWithText("Some header there").assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.tap() {
        // MultipleEventsCutter uses the real JVM wall clock.
        Thread.sleep(650)
        performClick()
        compose.waitForIdle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    private fun ComposeContentTestRule.show(content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(
                LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = true),
                content = content
            )
        }
    }
}
