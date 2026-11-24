package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AssetSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun selectorShowsTitleBalanceBadgeAndReportsTaps() {
        var taps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetSelector(
                        state = AssetSelectorState(title = "Kusama", iconUrl = "", balance = "300 KSM", badge = "Pool"),
                        onClick = { taps++ }
                    )
                    AssetSelector(state = AssetSelectorState(title = "Polkadot", iconUrl = "", balance = "1 DOT"))
                }
            }
        }

        compose.onNodeWithText("Kusama", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("300 KSM", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Pool", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 DOT", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Kusama").assertExists()

        compose.onNodeWithText("300 KSM", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test
    fun badgesShowTheirLabelsAndReportTaps() {
        val clicks = mutableListOf<String>()
        val icon = GradientDrawable().apply { setSize(16, 16) }
        compose.setContent {
            FearlessAppTheme {
                Column {
                    Badge(text = "Text badge")
                    Badge(iconResId = R.drawable.ic_scan, labelResId = R.string.staking_redeem, onClick = { clicks += "res" })
                    Badge(icon = icon, labelResId = R.string.hidden_assets, onClick = { clicks += "drawable" })
                    Badge(icon = null, labelResId = R.string.label_testnet, onClick = { clicks += "none" })
                }
            }
        }

        compose.onNodeWithText("Text badge").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.staking_redeem)).performClick()
        compose.onNodeWithText(context.getString(R.string.hidden_assets)).performClick()
        compose.onAllNodesWithText(context.getString(R.string.label_testnet))[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("res", "drawable", "none"), clicks)
    }
}
