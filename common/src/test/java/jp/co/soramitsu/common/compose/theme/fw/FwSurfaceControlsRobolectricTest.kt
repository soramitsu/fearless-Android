package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FwSurfaceControlsRobolectricTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shouldSelectFiltersAndRouteChooserAndTextActions() {
        var selected by mutableStateOf(false)
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    FwSectionLabel("Networks")
                    FwFilterChip("Favorites", selected, { selected = !selected })
                    FwSelectorPill("All networks", { events += "chooser" })
                    FwTextAction("Done", { events += "done" })
                    FwTag("Read only")
                    FwCheckMark(contentDescription = "Selected network")
                    FwExpandChevron(selected, contentDescription = "Expand networks")
                    FwIconWell { Text("DOT") }
                }
            }
        }
        compose.onNodeWithText("Favorites").assertIsNotSelected().performClick()
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("All networks").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Read only").assertIsDisplayed()
        compose.onNodeWithText("DOT").assertIsDisplayed()
        compose.onNodeWithContentDescription("Selected network").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand networks").assertIsDisplayed()
        assertEquals(listOf("chooser", "done"), events)
    }

    @Test
    fun shouldExposeSelectedAndDisabledRowsAndAllCheckStates() {
        val events = mutableListOf<String>()
        var checked by mutableStateOf(ToggleableState.Off)
        compose.setContent {
            FearlessAppTheme {
                Column {
                    FwListRow(selected = true, onClick = { events += "row" }) { Text("Selected wallet") }
                    FwListRow(enabled = false, onClick = { events += "disabled" }) { Text("Unavailable wallet") }
                    FwListRow { Text("Information") }
                    Row {
                        FwTriStateCheck(checked, { checked = ToggleableState.On }, Modifier.testTag("check"))
                        FwTriStateCheck(ToggleableState.Indeterminate, { events += "mixed" }, Modifier.testTag("mixed"))
                    }
                }
            }
        }
        compose.onNodeWithText("Selected wallet").assertIsSelected().performClick()
        compose.onNodeWithText("Unavailable wallet").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Information").assertIsDisplayed()
        compose.onNodeWithTag("check").assertIsOff().performClick()
        compose.onNodeWithTag("check").assertIsOn()
        compose.onNodeWithTag("mixed").performClick()
        assertEquals(listOf("row", "mixed"), events)
    }

    @Test
    fun shouldPresentNoticeTitlesMessagesAndOptionalActions() {
        var retries = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    FwNotice("Connection paused", title = "Offline", tone = FwNoticeTone.Neutral)
                    FwNotice("Check the recipient", tone = FwNoticeTone.Warning)
                    FwNotice("Request failed", title = "Unable to send", tone = FwNoticeTone.Danger) {
                        FwTextAction("Retry", { retries++ })
                    }
                }
            }
        }
        listOf("Connection paused", "Offline", "Check the recipient", "Request failed", "Unable to send").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun shouldKeepRecoveryCalmAndDisableUnavailableActions() {
        var calm = false
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, false)) {
                    FwRecoveryAppearance {
                        calm = LocalFwEffects.current.calm
                        FwRecoveryCard {
                            Text("Recovery words")
                            FwRecoveryButton("Continue", { events += "continue" })
                            FwRecoveryButton("Unavailable", { events += "disabled" }, enabled = false)
                            FwRecoveryTextButton("Back", { events += "back" })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Recovery words").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Unavailable").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Back").performClick()
        assertTrue(calm)
        assertEquals(listOf("continue", "back"), events)
    }
}
