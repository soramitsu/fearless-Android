package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
class FullScreenLoadingTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var loading by mutableStateOf(true)

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun loadingHidesTheContentBehindTheLoaderUntilItEnds() {
        var clicks = 0
        compose.show {
            FullScreenLoading(isLoading = loading, contentAlignment = Alignment.Center) {
                AccentButton(text = "Pay") { clicks++ }
            }
        }

        // While loading the content leaves the semantics tree and the loader announces itself.
        compose.onNodeWithText("Pay").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()

        loading = false
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertDoesNotExist()
        compose.onNodeWithText("Pay").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun customIndicatorReplacesTheDefaultLoader() {
        compose.show {
            FullScreenLoading(isLoading = true, indicator = { Text("Signing...") }) {
                Text("Hidden content")
            }
        }

        compose.onNodeWithText("Signing...").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertDoesNotExist()
        compose.onNodeWithText("Hidden content").assertDoesNotExist()
    }

    @Test
    fun fearlessProgressIsAnIndeterminateLoaderWithItsLabel() {
        compose.show { FearlessProgress() }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fw_loading_in_progress)).assertIsDisplayed()
    }

    @Test
    fun progressDialogOpensAWindowWithASpinner() {
        compose.mainClock.autoAdvance = false
        compose.show { ProgressDialog() }
        compose.mainClock.advanceTimeBy(300)

        compose.onNode(isDialog()).assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
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
