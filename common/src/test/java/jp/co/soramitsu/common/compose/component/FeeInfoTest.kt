package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class FeeInfoTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun knownFeeShowsAmountsAndTooltipReportsClicks() {
        var tooltips = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    FeeInfo(
                        state = FeeInfoViewState(caption = "Swap fee", feeAmount = "0.0051 KSM", feeAmountFiat = "$0.0009", tooltip = true),
                        tooltipClick = { tooltips++ }
                    )
                    FeeInfo(state = FeeInfoViewState(feeAmount = "1 DOT", feeAmountFiat = null))
                }
            }
        }

        compose.onNodeWithText("0.0051 KSM").assertIsDisplayed()
        compose.onNodeWithText("$0.0009").assertIsDisplayed()
        compose.onNodeWithText("1 DOT").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_network_fee)).assertHasNoClickAction()
        compose.onNodeWithText("Swap fee").performClick()
        compose.waitForIdle()

        assertEquals(1, tooltips)
        compose.onAllNodesWithTag("fee_estimate_loader").assertCountEquals(0)
    }

    @Test
    fun unknownFeeShowsTheEstimateLoader() {
        val state = FeeInfoViewState.default
        assertNull(state.feeAmount)
        compose.setContent { FearlessAppTheme { FeeInfo(state = state) } }

        compose.onNodeWithText(context.getString(R.string.common_network_fee)).assertIsDisplayed()
        compose.onAllNodesWithTag("fee_estimate_loader").assertCountEquals(1)
    }

    @Test
    fun previewShowsKnownAndLoadingFees() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.FeeInfoKt", "FeeInfo") }
        }

        compose.onNodeWithText("0.0051 KSM").assertIsDisplayed()
        compose.onAllNodesWithTag("fee_estimate_loader").assertCountEquals(1)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
