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
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class AssetListItemTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val base = AssetListItemViewState(
        index = 0,
        assetIconUrl = "",
        assetName = "Polkadot",
        assetChainName = "Relay",
        assetSymbol = "dot",
        assetTokenFiat = "$5.10",
        assetTokenRate = "+1.5%",
        assetTransferableBalance = "12.5",
        assetTransferableBalanceFiat = "$63.75",
        assetChainUrls = mapOf("a" to "", "b" to ""),
        chainId = "polkadot",
        chainAssetId = "dot",
        isSupported = true,
        isHidden = false,
        isTestnet = false
    )

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun verifiedRowShowsNameSymbolBalancesAndReportsClicks() {
        var clicked: AssetListItemViewState? = null
        compose.setContent {
            FearlessAppTheme { AssetListItem(state = base, onClick = { clicked = it }) }
        }

        compose.onNodeWithTag("AssetListItem_dot_chain_name", useUnmergedTree = true).assertTextEquals("POLKADOT · RELAY")
        compose.onNodeWithTag("AssetListItem_dot_symbol", useUnmergedTree = true).assertTextEquals("DOT")
        compose.onNodeWithTag("AssetListItem_dot_transferable", useUnmergedTree = true).assertTextEquals("12.5")
        compose.onNodeWithTag("AssetListItem_dot_transferable_fiat", useUnmergedTree = true).assertTextEquals("$63.75")
        compose.onNodeWithTag("AssetListItem_dot_change_fiat", useUnmergedTree = true).assertTextEquals("$5.10")
        compose.onNodeWithTag("AssetListItem_dot_change_percent", useUnmergedTree = true).assertTextEquals("+1.5%")
        // A verified asset carries no trust label and no transferable line unless asked for.
        compose.onAllNodesWithTag("AssetListItem_dot_trust", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("AssetListItem_dot_transferable_line", useUnmergedTree = true).assertCountEquals(0)

        compose.onNodeWithTag("AssetListItem_dot_Polkadot").performClick()
        compose.waitForIdle()
        assertEquals(base, clicked)
    }

    @Test
    fun totalBalanceWinsAndTransferableLineIsAddedWhenRequested() {
        val state = base.copy(
            assetTotalBalance = "20",
            assetTotalBalanceFiat = "$102",
            showTransferableBalance = true,
            assetTokenRate = "-3%",
            assetChainName = ""
        )
        compose.setContent {
            FearlessAppTheme { AssetListItem(state = state, onClick = {}) }
        }

        compose.onNodeWithTag("AssetListItem_dot_chain_name", useUnmergedTree = true).assertTextEquals("POLKADOT")
        compose.onNodeWithTag("AssetListItem_dot_transferable", useUnmergedTree = true).assertTextEquals("20")
        compose.onNodeWithTag("AssetListItem_dot_transferable_fiat", useUnmergedTree = true).assertTextEquals("$102")
        compose.onNodeWithTag("AssetListItem_dot_transferable_line", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.wallet_send_transferable_amount_caption, "12.5"))
        compose.onNodeWithTag("AssetListItem_dot_change_percent", useUnmergedTree = true).assertTextEquals("-3%")
    }

    @Test
    fun trustLabelsAndMissingPricesExplainThemselves() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetListItem(
                        state = base.copy(assetSymbol = "aaa", metadataTrust = "missing", assetTransferableBalanceFiat = null),
                        onClick = {}
                    )
                    AssetListItem(
                        state = base.copy(
                            assetSymbol = "bbb",
                            metadataTrust = "unverified",
                            isTestnet = true,
                            assetTransferableBalanceFiat = null,
                            assetTokenRate = null,
                            assetTokenFiat = null
                        ),
                        onClick = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("AssetListItem_aaa_trust", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.portfolio_asset_metadata_missing))
        compose.onNodeWithTag("AssetListItem_aaa_transferable_fiat", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.portfolio_price_unavailable))
        compose.onNodeWithTag("AssetListItem_bbb_trust", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.portfolio_asset_unverified))
        compose.onNodeWithTag("AssetListItem_bbb_transferable_fiat", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.portfolio_testnet_no_value))
        // The testnet badge carries the upper-cased label.
        compose.onNodeWithText(context.getString(R.string.label_testnet).uppercase(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun loadingRowAndShimmerRowShowPlaceholders() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetListItem(
                        state = base.copy(assetTransferableBalance = null, assetTotalBalance = null),
                        onClick = {}
                    )
                    AssetListItemShimmer(state = AssetListItemShimmerViewState(assetIconUrl = "", assetChainUrls = listOf("", "")))
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)

        compose.onAllNodesWithTag("AssetListItem_dot_transferable", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("AssetListItem_shimmer").assertExists()
        compose.onNodeWithTag("AssetListItem_shimmer_chains", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("AssetChainsBadge_chain_icon", useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun testnetBadgeShowsUpperCasedLabel() {
        compose.setContent {
            FearlessAppTheme { TestnetBadge() }
        }

        compose.onNodeWithText(context.getString(R.string.label_testnet).uppercase()).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.label_testnet)).assertCountEquals(0)
    }
}
