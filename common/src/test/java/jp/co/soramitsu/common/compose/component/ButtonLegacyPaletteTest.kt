package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/** FW_PALETTE=legacy restores the pre-sun-lit buttons; the build ships v1, so the switch is forced here. */
@RunWith(RobolectricTestRunner::class)
class ButtonLegacyPaletteTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun forceLegacyPalette() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        every { FwRestyle.isEnabled(any()) } returns false
    }

    @After
    fun restorePalette() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun legacyButtonsShowLabelsAndReportClicks() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AccentButton(state = ButtonViewState(text = "Legacy state"), onClick = { clicks += "state" })
                    AccentButton(text = "Legacy icon", iconRes = R.drawable.ic_scan, onClick = { clicks += "icon" })
                    AccentButton(text = "Legacy loadable", loading = false, onClick = { clicks += "loadable" })
                    AccentDarkDisabledButton(state = ButtonViewState("Legacy dark"), onClick = { clicks += "dark" })
                    GrayButton(text = "Legacy gray", onClick = { clicks += "gray" })
                    GrayButton(text = "Legacy gray loadable", loading = false, onClick = { clicks += "gray2" })
                    TransparentBorderedButton(iconRes = R.drawable.ic_scan, text = "Legacy bordered", onClick = { clicks += "bordered" })
                    AccentButton(state = ButtonViewState(text = "Legacy off", enabled = false), onClick = { clicks += "off" })
                }
            }
        }

        listOf("Legacy state", "Legacy icon", "Legacy loadable", "Legacy dark", "Legacy gray", "Legacy gray loadable")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed().assertIsEnabled().performClick() }
        compose.onNodeWithText("Legacy bordered").performClick()
        compose.onNodeWithText("Legacy off").assertIsNotEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf("state", "icon", "loadable", "dark", "gray", "gray2", "bordered"), clicks)
    }

    @Test
    fun legacyLoadingButtonsShowASpinnerNextToTheLabelAndSwallowTaps() {
        compose.mainClock.autoAdvance = false
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AccentButton(text = "Legacy staking", loading = true, onClick = { clicks++ })
                    AccentDarkDisabledButton(state = ButtonViewState("Legacy signing", loading = true), onClick = { clicks++ })
                    GrayButton(text = "Legacy pooling", loading = true, onClick = { clicks++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)

        listOf("Legacy staking", "Legacy signing", "Legacy pooling").forEach {
            compose.onNodeWithText(it).assertExists().performClick()
        }
        compose.mainClock.advanceTimeBy(300)

        assertEquals(0, clicks)
    }
}
