package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.purple
import jp.co.soramitsu.common.compose.theme.white08
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun sunlitAccentAndGrayButtonsShowTheirLabelsAndReportClicks() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AccentButton(state = ButtonViewState(text = "State accent"), onClick = { clicks += "state" })
                    AccentButton(text = "Icon accent", iconRes = R.drawable.ic_scan, onClick = { clicks += "icon" })
                    AccentButton(text = "Loading-capable accent", loading = false, onClick = { clicks += "loadable" })
                    AccentDarkDisabledButton(state = ButtonViewState("Dark accent"), onClick = { clicks += "dark" })
                    GrayButton(text = "Plain gray", onClick = { clicks += "gray" })
                    GrayButton(text = "Loadable gray", loading = false, onClick = { clicks += "gray2" })
                }
            }
        }

        listOf("State accent", "Icon accent", "Loading-capable accent", "Dark accent", "Plain gray", "Loadable gray").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertIsEnabled().performClick()
        }
        compose.waitForIdle()

        assertEquals(listOf("state", "icon", "loadable", "dark", "gray", "gray2"), clicks)
    }

    @Test
    fun disabledSunlitButtonsDropClicks() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AccentButton(state = ButtonViewState(text = "Off accent", enabled = false), onClick = { clicks++ })
                    AccentButton(text = "Off icon accent", enabled = false, iconRes = null, onClick = { clicks++ })
                    AccentDarkDisabledButton(state = ButtonViewState("Off dark", enabled = false), onClick = { clicks++ })
                    GrayButton(text = "Off gray", enabled = false, onClick = { clicks++ })
                }
            }
        }

        listOf("Off accent", "Off icon accent", "Off dark", "Off gray").forEach {
            compose.onNodeWithText(it).assertIsNotEnabled().performClick()
        }
        compose.waitForIdle()

        assertEquals(0, clicks)
    }

    @Test
    fun loadingSunlitButtonsKeepTheirLabelAndIgnoreTaps() {
        compose.mainClock.autoAdvance = false
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AccentButton(text = "Staking", loading = true, onClick = { clicks++ })
                    AccentButton(state = ButtonViewState(text = "Saving", loading = true), onClick = { clicks++ })
                    AccentDarkDisabledButton(state = ButtonViewState("Signing", loading = true), onClick = { clicks++ })
                    GrayButton(text = "Pooling", loading = true, onClick = { clicks++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)

        listOf("Staking", "Saving", "Signing", "Pooling").forEach {
            compose.onNodeWithText(it).assertExists().performClick()
        }
        compose.mainClock.advanceTimeBy(500)

        assertEquals(0, clicks)
    }

    @Test
    fun googleAndBorderedButtonsUseTheirLabels() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    GoogleButton(onClick = { clicks += "google" })
                    GoogleButton(text = "Google custom", backgroundColor = white08, borderColor = Color.Unspecified, onClick = { clicks += "custom" })
                    TransparentBorderedButton(iconRes = R.drawable.ic_scan, text = "Pre-installed", onClick = { clicks += "bordered" })
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.onboarding_connect_with_google)).assertIsDisplayed().performClick()
        compose.onNodeWithText("Google custom").performClick()
        compose.onNodeWithText("Pre-installed").performClick()
        compose.waitForIdle()

        assertEquals(listOf("google", "custom", "bordered"), clicks)
    }

    @Test
    fun legacyTextButtonsHonourEnabledStateAndDoubleTapGuard() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    TransparentButton(text = "Transparent", onClick = { clicks += "transparent" })
                    TransparentButton(text = "Transparent off", enabled = false, onClick = { clicks += "transparent-off" })
                    TextButton(text = "Text with icon", colors = customButtonColors(colorAccent), iconRes = R.drawable.ic_scan, onClick = { clicks += "text" })
                    FearlessButton(text = "Fearless", enabled = true, colors = customButtonColors(purple), onClick = { clicks += "fearless" })
                    TextButtonSmall(text = "Small", colors = customButtonColors(colorAccent), modifier = Modifier.height(24.dp), onClick = { clicks += "small" })
                    TextButtonSmall(text = "Small off", enabled = false, colors = customButtonColors(colorAccent), onClick = { clicks += "small-off" })
                }
            }
        }

        compose.onNodeWithText("Transparent").performClick()
        compose.onNodeWithText("Transparent off").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Text with icon").performClick()
        compose.onNodeWithText("Fearless").performClick()
        // A second tap on the same button inside the guard window is dropped.
        compose.onNodeWithText("Fearless").performClick()
        compose.onNodeWithText("Small").performClick()
        compose.onNodeWithText("Small off").assertIsNotEnabled()
        compose.waitForIdle()

        assertEquals(listOf("transparent", "text", "fearless", "small"), clicks)
    }

    @Test
    fun coloredAndShapeButtonsRenderTheirContent() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ColoredTextButton(text = "watch", backgroundColor = purple, onClick = { clicks += "watch" })
                    ColoredTextButton(text = "muted", enabled = false, backgroundColor = purple, onClick = { clicks += "muted" })
                    ColoredButton(backgroundColor = white08, border = BorderStroke(1.dp, colorAccent), onClick = { clicks += "colored" }) {
                        Text("Colored content")
                    }
                    ShapeButton(backgroundColor = white08, onClick = { clicks += "shape" }) {
                        Text("Shape content")
                    }
                    ShapeButton(backgroundColor = white08, enabled = false, onClick = { clicks += "shape-off" }) {
                        Text("Shape off")
                    }
                }
            }
        }

        // CapsTitle upper-cases its text.
        compose.onNodeWithText("WATCH").performClick()
        compose.onNodeWithText("MUTED").assertIsNotEnabled()
        compose.onNodeWithText("Colored content").performClick()
        compose.onNodeWithText("Shape content").performClick()
        compose.onNodeWithText("Shape off").assertIsNotEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf("watch", "colored", "shape"), clicks)
    }

    @Test
    fun previewShowsEveryButtonKind() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ButtonPreview() }
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("Start staking Start staking Start staking").assertExists()
        compose.onNodeWithText("WATCH").assertExists()
        compose.onNodeWithText("Create pool").assertExists()
        compose.onNodeWithText("Dark Button text").assertExists()
        compose.onAllNodesWithText("Get a pre-installed wallet").assertCountEquals(2)
        compose.onNodeWithText("Google").assertExists()
        compose.onNodeWithText(context.getString(R.string.onboarding_connect_with_google)).assertExists()
    }
}
