package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/** Wallet rows and the wallet chooser with FW_PALETTE=legacy (the build ships v1, so the switch is forced). */
@RunWith(RobolectricTestRunner::class)
class WalletLegacyPaletteTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val wallet = WalletItemViewState(
        id = 9,
        balance = "88.10",
        assetSymbol = "$",
        changeBalanceViewState = ChangeBalanceViewState(percentChange = "+1%", fiatChange = "$0.88"),
        title = "Legacy wallet",
        walletIcon = R.drawable.ic_wallet,
        isSelected = true,
        score = 40
    )

    @Before
    fun forceLegacyPalette() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        every { FwRestyle.isEnabled(any()) } returns false
    }

    @After
    fun restorePalette() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun legacyWalletRowShowsBalanceAndRoutesActions() {
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column(Modifier.testTag("row")) {
                    WalletItem(
                        state = wallet,
                        onOptionsClick = { events += "options" },
                        onSelected = { events += "selected" },
                        onLongClick = { events += "long" },
                        onScoreClick = { events += "score" }
                    )
                }
            }
        }

        compose.onNodeWithText("Legacy wallet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("$88.10", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("balance_change_fiat", useUnmergedTree = true).assertTextEquals("($0.88)")

        compose.onNodeWithContentDescription(context.getString(R.string.ux_more)).performClick()
        compose.onNodeWithText("40", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Legacy wallet").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertTrue(events.toString(), events.containsAll(listOf("options", "score", "selected")))
    }

    @Test
    fun legacyWalletRowPlaceholdersAndCaption() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                Column {
                    WalletItem(state = wallet.copy(isSelected = false, balance = null, changeBalanceViewState = null, score = null))
                    WalletItem(state = wallet.copy(id = 10, title = "Captioned", balance = null, changeBalanceViewState = null, balanceCaption = "incl. hidden"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)

        compose.onNodeWithText("Captioned", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("—", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("WalletItem_balance_caption", useUnmergedTree = true).assertTextEquals("incl. hidden")
        compose.onAllNodesWithContentDescription(context.getString(R.string.ux_more)).assertCountEquals(0)
    }

    @Test
    fun legacyWalletNameRowShowsSubtitleAndOptions() {
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    WalletNameItem(
                        state = WalletNameItemViewState(id = 1, title = "Name row", walletIcon = R.drawable.ic_wallet, isSelected = true, subtitle = "From extension"),
                        onOptionsClick = { events += "options" },
                        onSelected = { events += "selected" },
                        onLongClick = { events += "long" }
                    )
                    WalletNameItem(
                        state = WalletNameItemViewState(id = 2, title = "Plain row", walletIcon = R.drawable.ic_wallet, isSelected = false),
                        onSelected = { events += "plain" }
                    )
                }
            }
        }

        compose.onNodeWithText("From extension", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_more)).performClick()
        compose.onNodeWithText("Plain row", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Name row", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()

        assertTrue(events.toString(), events.containsAll(listOf("options", "plain", "long")))
    }

    @Test
    fun legacyChooserListsWalletsAndHandlesBack() {
        val picked = mutableListOf<Long>()
        var backs = 0
        compose.setContent {
            FearlessAppTheme {
                WalletSelectorScreen(
                    state = WalletSelectorViewState(
                        wallets = listOf(wallet.copy(id = 1, title = "One", score = null), wallet.copy(id = 2, title = "Two", score = null)),
                        selectedWallet = wallet.copy(id = 2)
                    ),
                    onWalletSelected = { picked += it.id },
                    onBackClicked = { backs++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_title_select_wallet)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("One").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.waitForIdle()

        assertTrue(picked.toString(), picked.isNotEmpty() && picked.all { it == 1L })
        assertEquals(1, backs)
    }
}
