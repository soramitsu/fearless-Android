package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class InfoTableItemTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun valuesAreShownAndLongAdditionalValuesAreShortened() {
        compose.show {
            Column {
                InfoTableItem(TitleValueViewState("From", "Account for Join", "0xEBN4KURhvkURhvkURhvkURhvkURhvkURhvk"))
                InfoTableItem(TitleValueViewState("Fee", "0.01 KSM", "short", valueColor = Color.Green))
                InfoTableItem(TitleValueViewState("Pending", null, null))
            }
        }

        compose.onNodeWithText("From", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Account for Join", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0xEBN4KU...hvkURhvk", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("short", useUnmergedTree = true).assertIsDisplayed()
        // A missing value shows a loading placeholder instead of text.
        compose.onNodeWithText("Pending", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("null", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun titleAndValueIconsReportTheirIdentifierWithoutARowHandler() {
        val clicks = mutableListOf<Int>()
        compose.show {
            Column {
                InfoTableItem(
                    TitleValueViewState("Title tap", "1 KSM", clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 7)),
                    onClick = { clicks += it }
                )
                InfoTableItem(
                    TitleValueViewState("Value tap", "2 KSM", clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_info_14, 9)),
                    onClick = { clicks += it }
                )
                // Without a value nothing is clickable.
                InfoTableItem(
                    TitleValueViewState("Loading", null, clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_info_14, 11)),
                    onClick = { clicks += it }
                )
            }
        }

        compose.onNode(hasText("Title tap") and hasClickAction()).tap()
        compose.onNode(hasText("2 KSM") and hasClickAction()).tap()

        assertEquals(listOf(7, 9), clicks)
    }

    @Test
    fun rowHandlerTakesPrecedenceWhenValueAndClickStateArePresent() {
        val rowClicks = mutableListOf<Int>()
        val itemClicks = mutableListOf<Int>()
        compose.show {
            InfoTable(
                items = listOf(
                    TitleValueViewState("Validator", "Alice", clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_info_14, 3)),
                    TitleValueViewState("Pool", "Bob", clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 4)),
                    TitleValueViewState("Plain", "Carol")
                ),
                onItemClick = { itemClicks += it },
                onRowClick = { rowClicks += it }
            )
        }

        compose.onNode(hasText("Validator") and hasClickAction()).tap()
        compose.onNode(hasText("Pool") and hasClickAction()).tap()
        compose.onNodeWithText("Carol", useUnmergedTree = true).assertIsDisplayed()

        assertEquals(listOf(3, 4), rowClicks)
        assertEquals(emptyList<Int>(), itemClicks)
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
