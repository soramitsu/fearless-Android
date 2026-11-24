package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/** Asset rows, balance header, selector and badges with FW_PALETTE=legacy (the build ships v1). */
@RunWith(RobolectricTestRunner::class)
class AssetLegacyPaletteTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

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
    fun legacyAssetRowShowsItsValuesAndReportsClicks() {
        val state = AssetListItemViewState(
            index = 1,
            assetIconUrl = "",
            assetName = "Kusama",
            assetChainName = "",
            assetSymbol = "ksm",
            assetTokenFiat = "$20",
            assetTokenRate = "-0.4%",
            assetTransferableBalance = "3",
            assetTransferableBalanceFiat = "$60",
            assetChainUrls = emptyMap(),
            chainId = "kusama",
            chainAssetId = "ksm",
            isSupported = true,
            isHidden = true,
            isTestnet = false
        )
        var clicked: AssetListItemViewState? = null
        compose.setContent {
            FearlessAppTheme { AssetListItem(state = state, onClick = { clicked = it }) }
        }

        compose.onNodeWithTag("AssetListItem_ksm_symbol", useUnmergedTree = true).assertTextEquals("KSM")
        compose.onNodeWithTag("AssetListItem_ksm_transferable", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("AssetListItem_ksm_change_percent", useUnmergedTree = true).assertTextEquals("-0.4%")
        compose.onNodeWithTag("AssetListItem_ksm_Kusama").performClick()
        compose.waitForIdle()

        assertEquals(state, clicked)
    }

    @Test
    fun legacyBalanceHeaderShowsAmountCaptionAndAddress() {
        var addressTaps = 0
        var balanceTaps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetBalance(
                        state = AssetBalanceViewState(
                            transferableBalance = "$9.99",
                            address = "0xabc",
                            isInfoEnabled = true,
                            changeViewState = ChangeBalanceViewState(percentChange = "+3%", fiatChange = "$0.30"),
                            balanceCaption = "Total"
                        ),
                        onAddressClick = { addressTaps++ },
                        onBalanceClick = { balanceTaps++ }
                    )
                    AssetBalance(
                        state = AssetBalanceViewState(transferableBalance = null, address = "", changeViewState = null),
                        onAddressClick = {}
                    )
                }
            }
        }

        compose.onNodeWithText("$9.99", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("—", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("balance_caption", useUnmergedTree = true).assertTextEquals("Total")
        compose.onNodeWithTag("balance_change_percent", useUnmergedTree = true).assertTextEquals("+3%")
        compose.onAllNodesWithTag("balance_address").assertCountEquals(1)

        compose.onAllNodesWithTag("balance_fiat")[0].performClick()
        compose.onNodeWithTag("balance_address").performClick()
        compose.waitForIdle()

        assertEquals(1, balanceTaps)
        assertEquals(1, addressTaps)
    }

    @Test
    fun legacySelectorAndIconBadge() {
        var selectorTaps = 0
        var badgeTaps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetSelector(state = AssetSelectorState(title = "Legacy asset", iconUrl = "", balance = "5 DOT"), onClick = { selectorTaps++ })
                    Badge(iconResId = R.drawable.ic_scan, labelResId = R.string.staking_redeem, onClick = { badgeTaps++ })
                }
            }
        }

        compose.onNodeWithText("Legacy asset", useUnmergedTree = true).performClick()
        compose.onNodeWithText(context.getString(R.string.staking_redeem)).performClick()
        compose.waitForIdle()

        assertEquals(1, selectorTaps)
        assertEquals(1, badgeTaps)
    }
}
