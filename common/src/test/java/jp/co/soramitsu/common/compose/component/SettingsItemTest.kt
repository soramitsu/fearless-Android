package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
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
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class SettingsItemTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun everyTrailingActionShowsItsLabelAndReportsRowClicks() {
        val clicks = mutableListOf<String>()
        compose.show {
            Column {
                SettingsItem(icon = painterResource(R.drawable.ic_settings_wallets), text = "Wallets", onClick = { clicks += "wallets" })
                SettingsItem(
                    icon = painterResource(R.drawable.ic_settings_wallets),
                    text = "Language",
                    action = SettingsItemAction.Selector("English"),
                    onClick = { clicks += "language" }
                )
                SettingsItem(
                    icon = painterResource(R.drawable.ic_settings_wallets),
                    text = "Backup",
                    action = SettingsItemAction.TransitionWithIcon(R.drawable.ic_warning_filled),
                    onClick = { clicks += "backup" }
                )
            }
        }

        compose.onNodeWithText("English", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Wallets").tap()
        compose.onNodeWithText("Language").tap()
        compose.onNodeWithText("Backup").tap()

        assertEquals(listOf("wallets", "language", "backup"), clicks)
    }

    @Test
    fun switchActionShowsItsValueAndBothRowAndSwitchReachTheCallback() {
        var clicks = 0
        compose.show {
            Column {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_settings_wallets),
                    text = "Sound",
                    action = SettingsItemAction.Switch(true),
                    onClick = { clicks++ }
                )
                SettingsItem(
                    icon = painterResource(R.drawable.ic_settings_wallets),
                    text = "Haptics",
                    action = SettingsItemAction.Switch(false)
                )
            }
        }

        val switches = compose.onAllNodes(isToggleable())
        switches.assertCountEquals(2)
        switches[0].assertIsOn()
        switches[1].assertIsOff()

        switches[0].tap()
        compose.onNodeWithText("Sound").tap()

        assertEquals(2, clicks)
    }

    @Test
    fun fearlessSwitchReportsTheRequestedValueAndFollowsState() {
        val reported = mutableListOf<Boolean>()
        compose.show {
            var checked by remember { mutableStateOf(false) }
            FearlessSwitch(isChecked = checked, onCheckedChange = {
                reported += it
                checked = it
            })
        }

        val toggle = compose.onNode(isToggleable())
        toggle.assertIsOff()
        toggle.tap()
        toggle.assertIsOn()
        toggle.tap()
        toggle.assertIsOff()

        assertEquals(listOf(true, false), reported)
    }

    @Test
    fun legacySwitchColorsAreWhiteThumbOnTransparentTrack() {
        var thumb = Color.Unspecified
        var track = Color.Unspecified
        compose.show {
            thumb = switchColors.thumbColor(enabled = true, checked = true).value
            track = switchColors.trackColor(enabled = false, checked = false).value
        }
        compose.waitForIdle()

        assertEquals(white, thumb)
        assertEquals(transparent, track)
    }

    @Test
    fun previewShowsEveryActionVariant() {
        compose.show { SettingsItemPreview() }

        compose.onAllNodesWithText("Item").assertCountEquals(5)
        compose.onNodeWithText("Value", useUnmergedTree = true).assertIsDisplayed()
        val switches = compose.onAllNodes(isToggleable())
        switches.assertCountEquals(2)
        switches[0].assertIsOn()
        switches[1].assertIsOff()
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
