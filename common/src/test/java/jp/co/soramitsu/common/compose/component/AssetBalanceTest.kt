package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
class AssetBalanceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val state = AssetBalanceViewState(
        transferableBalance = "$1,500.25",
        address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
        isInfoEnabled = true,
        changeViewState = ChangeBalanceViewState(percentChange = "-1.2%", fiatChange = "$18.00", percentFiatChange = "-$18"),
        balanceCaption = "Transferable"
    )

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun balanceHeaderShowsAmountChangeCaptionAndAddressAndReportsTaps() {
        var addressTaps = 0
        var balanceTaps = 0
        compose.setContent {
            FearlessAppTheme {
                AssetBalance(state = state, onAddressClick = { addressTaps++ }, onBalanceClick = { balanceTaps++ })
            }
        }

        compose.onNodeWithTag("balance_fiat").assertIsDisplayed()
        compose.onNodeWithTag("balance_change_percent", useUnmergedTree = true).assertTextEquals("-1.2%")
        compose.onNodeWithTag("fiat_balance_change_percent", useUnmergedTree = true).assertTextEquals("-$18")
        compose.onNodeWithTag("balance_change_fiat", useUnmergedTree = true).assertTextEquals("($18.00)")
        compose.onNodeWithTag("balance_caption", useUnmergedTree = true).assertTextEquals("Transferable")
        compose.onNodeWithTag("balance_address").assertIsDisplayed()

        compose.onNodeWithTag("balance_fiat").performClick()
        compose.onNodeWithTag("balance_address").performClick()
        compose.waitForIdle()

        assertEquals(1, balanceTaps)
        assertEquals(1, addressTaps)
    }

    @Test
    fun copyChipConfirmsTheCopyThenShowsTheAddressAgain() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme { AssetBalance(state = state, onAddressClick = {}) }
        }
        compose.mainClock.advanceTimeBy(100)

        compose.onNodeWithTag("balance_address").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(context.getString(R.string.common_copied), useUnmergedTree = true).assertExists()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onAllNodesWithText(context.getString(R.string.common_copied), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(state.address, useUnmergedTree = true).assertExists()
    }

    @Test
    fun missingBalanceShowsADashAndOptionalPartsStayHidden() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetBalance(
                        state = state.copy(
                            transferableBalance = null,
                            address = "",
                            isInfoEnabled = false,
                            changeViewState = ChangeBalanceViewState(percentChange = "+0%", fiatChange = ""),
                            balanceCaption = null
                        ),
                        onAddressClick = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("balance_fiat").assertIsDisplayed()
        compose.onAllNodesWithTag("balance_address").assertCountEquals(0)
        compose.onAllNodesWithTag("balance_caption", useUnmergedTree = true).assertCountEquals(0)
        // A change without a fiat amount is not shown at all.
        compose.onAllNodesWithTag("balance_change_percent", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun shimmerPlaceholderRendersWhileLoading() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetBalanceShimmer()
                    B1(text = "after shimmer")
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)

        compose.onNodeWithText("after shimmer").assertExists()
        compose.onAllNodesWithTag("balance_fiat").assertCountEquals(0)
    }
}
