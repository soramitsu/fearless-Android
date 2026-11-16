package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FwRollingTextRobolectricTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shouldDisplayCompleteAmountsAfterIncreasingAndDecreasingBalances() {
        var amount by mutableStateOf("9.9 DOT")
        compose.setContent {
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, false)) {
                    FwRollingText(amount, TextStyle(fontSize = 24.sp))
                }
            }
        }
        compose.onNodeWithText("9.9 DOT").assertIsDisplayed()
        for (next in listOf("10.2 DOT", "8.1 DOT", "8.1 KSM")) {
            compose.runOnIdle { amount = next }
            compose.onNodeWithText(next).assertIsDisplayed()
        }
        compose.onNodeWithText("8.1 KSM").assertIsDisplayed()
    }

    @Test
    fun shouldWrapNarrowAmountsAndKeepTheLatestValueOnSecureSurfaces() {
        var amount by mutableStateOf("1000 DOT")
        var secure by mutableStateOf(false)
        compose.setContent {
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Reduced, false, true), LocalFwSecureSurface provides secure) {
                    FwRollingText(amount, TextStyle(fontSize = 24.sp), Modifier.width(60.dp), roll = false)
                }
            }
        }
        compose.runOnIdle { amount = "2000 DOT" }
        compose.onNodeWithText("2000 DOT").assertIsDisplayed()
        compose.runOnIdle { secure = true; amount = "3000 DOT" }
        compose.onNodeWithText("3000 DOT").assertIsDisplayed()
    }

    @Test
    fun shouldRevealInitialRowsAndKeepLateRowsVisible() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, false)) {
                    val stagger = rememberFwStagger()
                    Column {
                        Text("First", Modifier.fwStaggerIn(stagger, 0))
                        Text("Second", Modifier.fwStaggerIn(stagger, 1))
                        Text("Late", Modifier.fwStaggerIn(stagger, 100))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        listOf("First", "Second", "Late").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}
