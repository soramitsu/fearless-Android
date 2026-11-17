package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where the list around a row already names the network (a network header, the selected network chip),
 * the row's caption keeps to the token. Read aloud, the row still names both.
 */
@RunWith(RobolectricTestRunner::class)
class AssetListItemNetworkNamingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun underANamedNetworkTheCaptionKeepsToTheTokenAndIsStillReadWithItsNetwork() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetListItem(state = row("usdt", "Tether", "Moonbeam"), showNetwork = false, onClick = {})
                    // A native token named after its network: the caption is its own name.
                    AssetListItem(state = row("glmr", "Moonbeam", "Moonbeam"), showNetwork = false, onClick = {})
                }
            }
        }

        caption("usdt").assertTextEquals("TETHER").assertContentDescriptionEquals("Tether, Moonbeam")
        caption("glmr").assertTextEquals("MOONBEAM").assertContentDescriptionEquals("Moonbeam")
    }

    @Test
    fun aTokenWithoutANameStillShowsAndSaysItsNetwork() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetListItem(state = row("aaa", "", "Moonbeam"), showNetwork = false, onClick = {})
                    AssetListItem(state = row("bbb", "", "Moonbeam"), onClick = {})
                }
            }
        }

        caption("aaa").assertTextEquals("MOONBEAM").assertContentDescriptionEquals("Moonbeam")
        caption("bbb").assertTextEquals("MOONBEAM").assertContentDescriptionEquals("Moonbeam")
    }

    private fun caption(symbol: String) = compose.onNodeWithTag("AssetListItem_${symbol}_chain_name", useUnmergedTree = true)

    private fun row(symbol: String, name: String, network: String) = AssetListItemViewState(
        index = 0,
        assetIconUrl = "",
        assetName = name,
        assetChainName = network,
        assetSymbol = symbol,
        assetTokenFiat = "$1.00",
        assetTokenRate = "+0.1%",
        assetTransferableBalance = "40",
        assetTransferableBalanceFiat = "$40.00",
        assetChainUrls = emptyMap(),
        chainId = "moonbeam",
        chainAssetId = symbol,
        isSupported = true,
        isHidden = false,
        isTestnet = false
    )
}
