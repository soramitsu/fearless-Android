package jp.co.soramitsu.onboarding.impl.welcome

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo
import jp.co.soramitsu.onboarding.impl.testutil.PrivateComposable
import jp.co.soramitsu.onboarding.impl.testutil.allowNextTap
import jp.co.soramitsu.onboarding.impl.testutil.drawOnce
import jp.co.soramitsu.onboarding.impl.testutil.text
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OnboardingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val callback = object : OnboardingScreenCallback {
        override fun onClose() { calls += "close" }
        override fun onSkip() { calls += "skip" }
    }

    @Before
    fun moveClockPastTheDoubleTapGuard() = allowNextTap()

    private fun slide(title: String, color: String = "#e6006f") = ScreenInfo(
        title = ScreenInfo.TitleInfo(title, color),
        description = "$title description",
        image = "file:///nonexistent/$title.png"
    )

    private fun showPager(flow: OnboardingFlow?, background: String? = null) {
        val flowState = MutableStateFlow(flow)
        val backgroundState = MutableStateFlow(background)
        compose.setContent {
            FearlessAppTheme {
                NavHost(rememberNavController(), startDestination = WelcomeEvent.Onboarding.PagerScreen.route) {
                    OnboardingScreen(backgroundState, flowState, callback)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
        allowNextTap()
    }

    @Test
    fun withoutSlidesThePagerWaitsWithALoader() {
        compose.mainClock.autoAdvance = false
        showPager(flow = null)

        compose.onAllNodesWithText(text(R.string.common_next)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.common_skip)).assertCountEquals(0)
    }

    @Test
    fun nextPagesThroughTheSlidesAndClosesOnTheLastOne() {
        showPager(OnboardingFlow(listOf(slide("First"), slide("Second", color = "not a colour"))))
        compose.onNodeWithText("First").assertIsDisplayed()
        compose.onNodeWithText("First description").assertIsDisplayed()

        tap(text(R.string.common_next))
        compose.onNodeWithText("Second").assertIsDisplayed()
        compose.activity.window.decorView.drawOnce()
        assertEquals(emptyList<String>(), calls)

        tap(text(R.string.common_next))
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun skipAndTheCloseIconLeaveTheSlides() {
        showPager(OnboardingFlow(listOf(slide("Only"))), background = "file:///nonexistent/background.png")

        tap(text(R.string.common_skip))
        compose.onNodeWithContentDescription(text(R.string.ux_close)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("skip", "close"), calls)
        compose.activity.window.decorView.drawOnce()
    }

    @Test
    fun previewShowsItsFirstSlide() {
        compose.setContent {
            PrivateComposable("jp.co.soramitsu.onboarding.impl.welcome.OnboardingScreenKt", "OnboardingScreenPreview")
        }
        compose.waitForIdle()

        compose.onNodeWithText("Brand new network management").assertIsDisplayed()
        compose.activity.window.decorView.drawOnce()
    }

    @Test
    fun splashScreenShowsTheBrandLine() {
        compose.setContent {
            FearlessAppTheme {
                NavHost(rememberNavController(), startDestination = WelcomeEvent.Onboarding.SplashScreen.route) {
                    OnboardingSplashScreen()
                }
            }
        }
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()

        compose.onNodeWithText(text(R.string.fw_brand_tagline), ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun splashPreviewShowsTheBrandLine() {
        compose.setContent {
            PrivateComposable("jp.co.soramitsu.onboarding.impl.welcome.OnboardingSplashScreenKt", "OnboardingSplashScreenPreview")
        }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.fw_brand_tagline), ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun pagerIndicatorDrawsOneDotPerPage() {
        val page = mutableIntStateOf(0)
        val offset = mutableFloatStateOf(0f)
        compose.setContent {
            FearlessAppTheme {
                ReflectivePagerIndicator(gap = 7.dp, size = 3, currentPage = page, slideOffset = offset) { index ->
                    Box(Modifier.size(7.dp).testTag("dot-$index"))
                }
            }
        }
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()

        compose.onAllNodesWithTag("dot-0").assertCountEquals(1)
        compose.onAllNodesWithTag("dot-2").assertCountEquals(1)
        compose.runOnIdle {
            page.intValue = 1
            offset.floatValue = -0.5f
        }
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()
        compose.runOnIdle { offset.floatValue = 0.5f }
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()
        compose.onAllNodesWithTag("dot-1").assertCountEquals(1)
    }

    @Test
    fun pagerIndicatorPreviewSwitchesPagesOnTap() {
        compose.setContent {
            FearlessAppTheme {
                PrivateComposable("jp.co.soramitsu.onboarding.impl.welcome.PagerIndicatorKt", "PreviewSlidingPagerIndicator")
            }
        }
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()

        compose.onNodeWithText("1").performClick()
        compose.waitForIdle()
        compose.activity.window.decorView.drawOnce()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun lerpInterpolatesLinearly() {
        assertEquals(10f, lerp(10f, 20f, 0f), 0f)
        assertEquals(15f, lerp(10f, 20f, 0.5f), 0f)
        assertEquals(20f, lerp(10f, 20f, 1f), 0f)
        assertEquals(5f, lerp(10f, 20f, -0.5f), 0f)
    }

    /**
     * PagerIndicator is an inline composable, so callers get a copy of its body; calling the compiled function
     * through reflection runs the function itself, as a non-inlining caller would.
     */
    @Composable
    private fun ReflectivePagerIndicator(
        gap: Dp,
        size: Int,
        currentPage: State<Int>,
        slideOffset: State<Float>,
        content: @Composable (Int) -> Unit
    ) {
        val method = Class.forName("jp.co.soramitsu.onboarding.impl.welcome.PagerIndicatorKt").declaredMethods
            .single { it.name.startsWith("PagerIndicator-") }
        method.invoke(null, Modifier, gap.value, size, currentPage, slideOffset, content, currentComposer, 0)
    }
}
