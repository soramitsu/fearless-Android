package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GradientIconStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gradientIconsAreLitWellsOfNinetyDpForEveryIconSource() {
        compose.show(glowing) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GradientIcon(iconRes = R.drawable.ic_vector, color = Color.Red, modifier = Modifier.testTag("local"))
                GradientIcon(icon = MISSING_SVG, color = Color.Red, modifier = Modifier.testTag("url"), tintImage = true)
                GradientIcon(icon = GradientIconState.Local(R.drawable.ic_vector), color = Color.Red, modifier = Modifier.testTag("state-local"))
                GradientIcon(icon = GradientIconState.Remote(url = "", color = "FF0000"), color = Color.Red, modifier = Modifier.testTag("remote-empty"))
                GradientIcon(icon = GradientIconState.Remote(url = MISSING_SVG, color = null), color = Color.Red, modifier = Modifier.testTag("remote-url"))
                GradientIcon(icon = GradientIconState.Remote(url = MISSING_SVG, color = ""), color = Color.Red, modifier = Modifier.testTag("remote-blank"))
            }
        }

        listOf("local", "url", "state-local", "remote-empty", "remote-url", "remote-blank").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed().assertWidthIsEqualTo(90.dp).assertHeightIsEqualTo(90.dp)
        }
    }

    @Test
    fun reducedEffectsKeepTheWellWithoutTheGlow() {
        compose.show(FwEffects(FwTier.Reduced, highContrast = false, reduceMotion = true)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GradientIcon(iconRes = R.drawable.ic_vector, color = Color.Yellow, modifier = Modifier.testTag("reduced"))
                DoubleGradientIcon(
                    leftImage = GradientIconState.Local(R.drawable.ic_vector),
                    rightImage = GradientIconState.Remote(url = "", color = "00FF00"),
                    modifier = Modifier.testTag("double")
                )
            }
        }

        compose.onNodeWithTag("reduced").assertWidthIsEqualTo(90.dp)
        compose.onNodeWithTag("double").assertIsDisplayed().assertHeightIsEqualTo(90.dp)
    }

    @Test
    fun haloIconsSizeTheirHaloAroundTheImage() {
        compose.show(glowing) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HaloIcon(iconRes = R.drawable.ic_vector, color = Color.Red, modifier = Modifier.testTag("halo-res"), contentPadding = PaddingValues(2.dp))
                HaloIcon(icon = MISSING_SVG, color = Color.Red, modifier = Modifier.testTag("halo-url"), tintImage = false)
                HaloIcon(icon = MISSING_SVG, color = Color.Red, modifier = Modifier.testTag("halo-url-tinted"))
                HaloIcon(icon = HaloIconState.Local(R.drawable.ic_vector), color = Color.Red, modifier = Modifier.testTag("halo-local"))
                HaloIcon(icon = HaloIconState.Remote("", "0000FF"), color = Color.Red, modifier = Modifier.testTag("halo-remote-empty"))
                HaloIcon(icon = HaloIconState.Remote(MISSING_SVG, null), color = Color.Red, modifier = Modifier.testTag("halo-remote-url"))
            }
        }

        // 90 dp image + 2 x 14 dp padding + 2 x 30 dp halo.
        compose.onNodeWithTag("halo-res").assertWidthIsEqualTo(178.dp).assertHeightIsEqualTo(178.dp)
        listOf("halo-url", "halo-url-tinted", "halo-local", "halo-remote-empty", "halo-remote-url").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed().assertWidthIsEqualTo(90.dp)
        }
    }

    @Test
    fun iconStatesKeepTheirSource() {
        val remote = GradientIconState.Remote("https://example.invalid/a.svg", "ABCDEF")
        val halo = HaloIconState.Remote("u", null)

        assertEquals("https://example.invalid/a.svg", remote.url)
        assertEquals("ABCDEF", remote.color)
        assertEquals(R.drawable.ic_vector, GradientIconState.Local(R.drawable.ic_vector).res)
        assertEquals("u", halo.url)
        assertEquals(R.drawable.ic_vector, HaloIconState.Local(R.drawable.ic_vector).res)
    }

    @Test
    fun doubleIconPreviewShowsTwoOverlappingIcons() {
        compose.setContent {
            FearlessAppTheme {
                Column(Modifier.testTag("preview")) { DoubleGradientIconPreview() }
            }
        }

        compose.onNodeWithTag("preview").assertIsDisplayed().assertHeightIsEqualTo(90.dp)
    }

    private fun ComposeContentTestRule.show(effects: FwEffects, content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(LocalFwEffects provides effects, content = content)
        }
    }

    private companion object {
        const val MISSING_SVG = "file:///android_asset/missing-icon.svg"
        val glowing = FwEffects(FwTier.Full, highContrast = false, reduceMotion = true)
    }
}
