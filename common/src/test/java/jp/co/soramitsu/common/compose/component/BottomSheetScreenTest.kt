package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class BottomSheetScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun sheetScreenAndDialogShowTheirContentUnderAGrip() {
        compose.show {
            Column {
                BottomSheetScreen(modifier = Modifier.testTag("screen")) { Text("Screen body") }
                BottomSheetDialog(modifier = Modifier.testTag("dialog"), verticalArrangement = Arrangement.Center) { Text("Dialog body") }
                Grip(Modifier.testTag("grip"))
            }
        }

        compose.onNodeWithText("Screen body").assertIsDisplayed()
        compose.onNodeWithText("Dialog body").assertIsDisplayed()
        compose.onNodeWithTag("screen").assertIsDisplayed()
        compose.onNodeWithTag("dialog").assertIsDisplayed()
        compose.onNodeWithTag("grip").assertWidthIsEqualTo(35.dp)
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun layoutOwningItsStateOpensTheSheetOnRequest() {
        compose.show {
            val scope = rememberCoroutineScope()
            BottomSheetLayout(
                content = { state ->
                    AccentButton(text = "Open", modifier = Modifier.testTag("open")) { scope.launch { state.show() } }
                },
                sheetContent = { state ->
                    Text(if (state.isVisible) "Sheet open" else "Sheet closed")
                }
            )
        }

        compose.onNodeWithText("Sheet closed").assertExists()
        compose.onNodeWithText("Open").tap()

        compose.onNodeWithText("Sheet open").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun layoutWithAGivenStateShowsAnExpandedSheet() {
        compose.show {
            val state = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Expanded)
            BottomSheetLayout(
                content = { Text("Behind") },
                sheetContent = { Text("Expanded sheet") },
                bottomSheetState = state,
                sheetGesturesEnabled = false
            )
        }

        compose.onNodeWithText("Expanded sheet").assertIsDisplayed()
        compose.onNodeWithText("Behind").assertExists()
    }

    @Test
    fun alertSheetShowsItsTextsAndRoutesCloseAndAction() {
        var closed = 0
        var toppedUp = 0
        compose.show {
            AlertSheet(
                state = AlertViewState(
                    title = "Network unavailable",
                    message = "Top up to continue",
                    buttonText = "Top up",
                    textSize = 14,
                    iconRes = R.drawable.ic_alert_16
                ),
                onBackClicked = { closed++ },
                onTopUpClicked = { toppedUp++ }
            )
        }

        compose.onNodeWithText("Network unavailable").assertIsDisplayed()
        compose.onNodeWithText("Top up to continue").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.common_close)).tap()
        compose.onNodeWithText("Top up").tap()

        assertEquals(1, closed)
        assertEquals(1, toppedUp)
    }

    @Test
    fun corneredBackgroundsWrapTheirContentForEveryColourChoice() {
        compose.show {
            Column {
                BackgroundCornered { Text("Default card") }
                BackgroundCornered(backgroundColor = Color.Transparent) { Text("Transparent card") }
                BackgroundCornered(backgroundColor = Color.Unspecified) { Text("Unspecified card") }
                BackgroundCornered(backgroundColor = Color.Red) { Text("Red card") }
                BackgroundCorneredWithBorder { Text("Neutral well") }
                BackgroundCorneredWithBorder(borderColor = Color.Red) { Text("Error well") }
                BackgroundCorneredWithBorder(borderColor = Color.Transparent) { Text("Clear well") }
                BackgroundCorneredWithGradientBorder { Text("Highlighted card") }
            }
        }

        listOf(
            "Default card", "Transparent card", "Unspecified card", "Red card",
            "Neutral well", "Error well", "Clear well", "Highlighted card"
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun corneredShapeOutlinesACutCornerPath() {
        val shape = FearlessCorneredShape(cornerRadius = 4.dp, cornerCutLength = 6.dp)
        val outline = shape.createOutline(
            androidx.compose.ui.geometry.Size(200f, 100f),
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.unit.Density(2f)
        )

        // The cut corners need a generic path; a rectangle or rounded outline would lose them.
        assertTrue(outline is androidx.compose.ui.graphics.Outline.Generic)
        assertNotNull((outline as androidx.compose.ui.graphics.Outline.Generic).path)
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
