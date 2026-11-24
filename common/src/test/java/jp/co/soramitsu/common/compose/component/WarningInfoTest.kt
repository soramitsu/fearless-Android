package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
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
class WarningInfoTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun warningWithDetailsExpandsOnTapAndListsThem() {
        compose.show {
            var expanded by remember { mutableStateOf(false) }
            WarningInfo(
                state = WarningInfoState(
                    message = "This address is flagged",
                    extras = listOf("Name" to "Scam address", "Reason" to "Phishing"),
                    isExpanded = expanded,
                    color = Color.Red
                ),
                onClick = { expanded = !expanded }
            )
        }

        compose.onNodeWithText(context.getString(R.string.common_warning), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("This address is flagged", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("warning_extra_Name", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("warning_layout").tap()

        compose.onNodeWithTag("warning_extra_Name", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Name:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Scam address", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Reason:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Phishing", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun warningWithoutDetailsStillReportsTaps() {
        var taps = 0
        compose.show {
            WarningInfo(state = WarningInfoState(message = "Low balance", extras = emptyList()), onClick = { taps++ })
        }

        compose.onNodeWithText("Low balance", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("warning_layout").tap()

        assertEquals(1, taps)
    }

    @Test
    fun attentionMessageUsesItsTitleWithoutTheTrailingColon() {
        compose.show {
            Column {
                AttentionMessage(attentionText = "Note:", message = "Claims take a day")
                AttentionMessage(message = "Default title")
            }
        }

        compose.onNodeWithText("Note", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Note:", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Claims take a day", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_warning).removeSuffix(":"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Default title", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun notificationShowsItsActionOnlyWithAHandler() {
        var actions = 0
        val state = NotificationState(
            iconRes = R.drawable.ic_status_warning_16,
            title = "Claimable",
            value = "0.49 KSM",
            buttonText = "Claim",
            color = Color.Yellow
        )
        compose.show {
            Column {
                Notification(state, onAction = { actions++ })
                Notification(state.copy(title = "Passive", buttonText = "Hidden"), onAction = null)
            }
        }

        compose.onNodeWithText("Claimable", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Passive", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Hidden").assertDoesNotExist()
        compose.onNodeWithText("Claim").tap()

        assertEquals(1, actions)
    }

    @Test
    fun networkIssuesBadgeShowsTheStubAndReportsTaps() {
        var taps = 0
        compose.show { NetworkIssuesBadge(onClick = { taps++ }) }

        compose.onNodeWithText(context.getString(R.string.network_issue_stub)).assertIsDisplayed().tap()

        assertEquals(1, taps)
    }

    private fun SemanticsNodeInteraction.tap() {
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
