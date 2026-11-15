package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
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
class EnterAmountScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val state = EnterAmountViewState(
        toolbarState = ToolbarViewState("Stake more", R.drawable.ic_arrow_back_24dp),
        amountInputState = AmountInputViewState(
            tokenName = "KSM",
            totalBalance = "1003 KSM",
            fiatAmount = "$170000",
            tokenAmount = BigDecimal("0.98"),
            title = "Amount to stake"
        ),
        feeInfoState = FeeInfoViewState("Network Fee", "0.000001 KSM", "$0.00045"),
        buttonState = ButtonViewState("Continue", true)
    )

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun screenShowsAmountFeeAndButtonAndReportsActions() {
        var navigation = 0
        var next = 0
        val amounts = mutableListOf<BigDecimal?>()
        compose.setContent {
            FearlessAppTheme {
                EnterAmountScreen(
                    state = state,
                    onNavigationClick = { navigation++ },
                    onAmountInput = { amounts += it },
                    onNextClick = { next++ }
                )
            }
        }

        compose.onNodeWithText("Stake more").assertIsDisplayed()
        compose.onNodeWithText("Amount to stake").assertIsDisplayed()
        compose.onNodeWithText("Network Fee").assertIsDisplayed()
        compose.onNodeWithText("0.000001 KSM").assertIsDisplayed()
        compose.onNodeWithTag("InputAmountFieldKSM").performTextReplacement("1.5")
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, navigation)
        assertEquals(1, next)
        assertEquals(BigDecimal("1.5"), amounts.last())
        // Without a quick-amount callback no percentages are offered.
        compose.onAllNodesWithText("50%").assertCountEquals(0)
    }

    @Test
    fun quickAmountsAppearOnlyWhileTheKeyboardIsOpen() {
        val quick = mutableListOf<Double>()
        var keyboardOpen by mutableStateOf(false)
        compose.setContent {
            FearlessAppTheme {
                EnterAmountScreen(
                    state = state,
                    isSoftKeyboardOpen = keyboardOpen,
                    onNavigationClick = {},
                    onAmountInput = {},
                    onQuickAmountInput = { quick += it },
                    onNextClick = {}
                )
            }
        }

        compose.onAllNodesWithText("75%").assertCountEquals(0)
        keyboardOpen = true
        compose.waitForIdle()
        compose.onNodeWithText("75%").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(0.75), quick)
    }

    @Test
    fun previewShowsTheStakeMoreForm() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.EnterAmountScreenKt", "EnterAmountScreenPreview") }
        }

        compose.onNodeWithText("Stake more").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertExists()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
