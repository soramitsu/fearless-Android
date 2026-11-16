package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AccentButtonRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        // Robolectric's clock starts near 100 ms, inside the buttons' double-tap guard window; a device has been up longer.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun enabledButtonShowsItsTextAndReportsEachClick() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                AccentButton(state = ButtonViewState(text = "Continue"), onClick = { clicks++ })
            }
        }

        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)

        // A second tap inside the guard window is a double tap and is dropped; one after it counts again.
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)

        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(2, clicks)
    }

    @Test
    fun disabledButtonIgnoresClicks() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                AccentButton(state = ButtonViewState(text = "Send", enabled = false), onClick = { clicks++ })
            }
        }

        compose.onNodeWithText("Send").assertIsNotEnabled().performClick()
        compose.waitForIdle()

        assertEquals(0, clicks)
    }
}
