package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.model.WalletEcosystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class WalletItemTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val wallet = WalletItemViewState(
        id = 7,
        balance = "1,234.50",
        assetSymbol = "$",
        changeBalanceViewState = ChangeBalanceViewState(percentChange = "+2.5%", fiatChange = "$30.10"),
        title = "Main wallet",
        walletIcon = R.drawable.ic_wallet,
        isSelected = true
    )

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun selectedWalletShowsBalanceChangeAndRoutesEveryGesture() {
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                WalletItem(
                    state = wallet,
                    onOptionsClick = { events += "options:${it.id}" },
                    onSelected = { events += "selected:${it.id}" },
                    onLongClick = { events += "long:${it.id}" }
                )
            }
        }

        compose.onNodeWithText("Main wallet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("$1,234.50", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("balance_change_percent", useUnmergedTree = true).assertTextEquals("+2.5%")
        compose.onNodeWithTag("balance_change_fiat", useUnmergedTree = true).assertTextEquals("($30.10)")
        compose.onNodeWithText("Main wallet").assertIsSelected()

        compose.onNodeWithText("Main wallet").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_more)).performClick()
        compose.onNodeWithText("Main wallet").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(listOf("selected:7", "options:7", "long:7"), events)
    }

    @Test
    fun unselectedCaptionedWalletShowsDashAndNoExtras() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    WalletItem(
                        state = wallet.copy(
                            isSelected = false,
                            balance = null,
                            changeBalanceViewState = null,
                            balanceCaption = "incl. hidden"
                        )
                    )
                }
            }
        }

        compose.onNodeWithText("Main wallet").assert(isSelected().not())
        compose.onNodeWithText("—", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("WalletItem_balance_caption", useUnmergedTree = true).assertTextEquals("incl. hidden")
        compose.onAllNodesWithContentDescription(context.getString(R.string.ux_more)).assertCountEquals(0)
        compose.onAllNodesWithTag("balance_change_percent", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun loadingWalletShowsPlaceholdersInsteadOfAmounts() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                WalletItem(state = wallet.copy(balance = null, changeBalanceViewState = null))
            }
        }
        compose.mainClock.advanceTimeBy(300)

        compose.onNodeWithText("Main wallet", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("WalletItem_balance_caption", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("balance_change_percent", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun ecosystemFlagsDescribeTonSupport() {
        val tonOnly = wallet.copy(supportedEcosystems = setOf(WalletEcosystem.Ton))
        val mixed = wallet.copy(supportedEcosystems = setOf(WalletEcosystem.Ton, WalletEcosystem.Substrate))
        val none = wallet.copy(supportedEcosystems = emptySet())

        assertTrue(tonOnly.supportsTon)
        assertTrue(tonOnly.onlyTonSupported)
        assertTrue(mixed.supportsTon)
        assertFalse(mixed.onlyTonSupported)
        assertFalse(none.supportsTon)
        assertFalse(none.onlyTonSupported)
    }
}
