package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AmountInputTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun defaultStateIsAnEmptyZeroAmount() {
        val state = AmountInputViewState.defaultObj

        assertNull(state.tokenName)
        assertEquals("0", state.totalBalance)
        assertEquals("$0", state.fiatAmount)
        assertEquals(BigDecimal.ZERO, state.tokenAmount)
        assertTrue(state.isActive)
        assertTrue(state.inputEnabled)
    }

    @Test
    fun activeInputShowsTokenBalanceAndFiatAndReportsEveryInteraction() {
        val inputs = mutableListOf<BigDecimal>()
        val focus = mutableListOf<Boolean>()
        var tokenClicks = 0
        var done = 0
        compose.setContent {
            FearlessAppTheme {
                AmountInput(
                    state = AmountInputViewState(
                        tokenName = "ksm",
                        totalBalance = "Balance: 20.0",
                        fiatAmount = "$120.0",
                        tokenAmount = BigDecimal("1.5"),
                        allowAssetChoose = true
                    ),
                    borderColorFocused = Color.Green,
                    onInput = { inputs += it },
                    onInputFocusChange = { focus += it },
                    onTokenClick = { tokenClicks++ },
                    onKeyboardDone = { done++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_amount)).assertIsDisplayed()
        compose.onNodeWithText("$120.0").assertIsDisplayed()
        compose.onNodeWithText("Balance: 20.0").assertIsDisplayed()
        compose.onNodeWithText("KSM").performClick()

        val field = compose.onNodeWithTag("InputAmountFieldksm")
        field.assertIsEnabled().performClick()
        field.performTextReplacement("2.25")
        field.performImeAction()
        compose.waitForIdle()

        assertEquals(1, tokenClicks)
        assertEquals(BigDecimal("2.25"), inputs.last())
        assertTrue(focus.contains(true))
        assertEquals(1, done)
    }

    @Test
    fun inactiveStackedInputWithoutTokenAsksToSelectAnAsset() {
        var tokenClicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AmountInput(
                        state = AmountInputViewState(
                            title = "You pay",
                            totalBalance = "Balance: 0",
                            fiatAmount = null,
                            tokenAmount = BigDecimal.ZERO,
                            isActive = false,
                            isFocused = true,
                            inputEnabled = false
                        ),
                        borderColorFocused = Color.Green,
                        onTokenClick = { tokenClicks++ },
                        stacked = true
                    )
                    AmountInput(
                        state = AmountInputViewState(
                            tokenName = "dot",
                            totalBalance = "Balance: 3",
                            fiatAmount = "$9",
                            tokenAmount = BigDecimal("3.000000000001"),
                            isActive = false,
                            isFocused = true
                        )
                    )
                }
            }
        }

        compose.onNodeWithText("You pay").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_select_asset)).performClick()
        compose.onNodeWithTag("InputAmountField").assertIsNotEnabled()
        compose.onNodeWithText("DOT").assertIsDisplayed()
        compose.onNodeWithText("3.000000000001", useUnmergedTree = true).assertIsDisplayed()
        compose.waitForIdle()

        // Without allowAssetChoose the token is not a button.
        assertEquals(0, tokenClicks)
    }

    @Test
    fun remoteTokenIconAndShimmeringAmountsKeepTheTexts() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                AmountInput(
                    state = AmountInputViewState(
                        tokenName = "xor",
                        tokenImage = "file:///android_asset/missing-token-icon.svg",
                        totalBalance = "Balance: 7",
                        fiatAmount = "$7",
                        tokenAmount = BigDecimal("7"),
                        isShimmerAmounts = true
                    )
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("XOR").assertIsDisplayed()
        compose.onNodeWithText("$7").assertIsDisplayed()
        compose.onNodeWithText("Balance: 7").assertIsDisplayed()
    }
}
