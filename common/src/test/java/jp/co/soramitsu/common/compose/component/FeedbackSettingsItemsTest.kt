package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
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
import jp.co.soramitsu.common.feedback.FeedbackPreferences
import jp.co.soramitsu.common.feedback.FeedbackSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class FeedbackSettingsItemsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        context.getSharedPreferences(FeedbackPreferences.STORE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun rowsStartOnAndEachTapStoresTheFlippedSetting() {
        var dividers = 0
        compose.show { Column { FeedbackSettingsItems(divider = { dividers++ }) } }

        compose.onNodeWithText(context.getString(R.string.fw_feedback_sound_effects)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fw_feedback_haptic_feedback)).assertIsDisplayed()
        assertTrue(dividers >= 2)
        val switches = compose.onAllNodes(isToggleable())
        switches.assertCountEquals(2)
        switches[0].assertIsOn()
        switches[1].assertIsOn()

        compose.onNodeWithTag("settings_sound_effects").tap()
        assertFalse(FeedbackPreferences(context).read().soundEffects)
        compose.onAllNodes(isToggleable())[0].assertIsOff()

        compose.onNodeWithTag("settings_haptic_feedback").tap()
        assertFalse(FeedbackPreferences(context).read().hapticFeedback)
        compose.onAllNodes(isToggleable())[1].assertIsOff()

        // Turning haptics back on plays the commit cue and stores the setting.
        compose.onNodeWithTag("settings_haptic_feedback").tap()
        assertTrue(FeedbackPreferences(context).read().hapticFeedback)
        compose.onAllNodes(isToggleable())[1].assertIsOn()
    }

    @Test
    fun statelessRowsReflectTheGivenSettingsAndRouteTheirTaps() {
        val taps = mutableListOf<String>()
        compose.show {
            Column {
                FeedbackSettingsRows(
                    settings = FeedbackSettings(soundEffects = false, hapticFeedback = true),
                    onSound = { taps += "sound" },
                    onHaptics = { taps += "haptics" },
                    divider = {}
                )
            }
        }

        val switches = compose.onAllNodes(isToggleable())
        switches[0].assertIsOff()
        switches[1].assertIsOn()
        switches[1].tap()
        switches[0].tap()

        assertEquals(listOf("haptics", "sound"), taps)
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
