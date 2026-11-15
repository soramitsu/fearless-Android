package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.filter
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetChainsBadgeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun upToFiveChainsAreAllShownWithoutACounter() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AssetChainsBadge(urls = List(5) { "" }, modifier = Modifier.testTag("five"))
                    AssetChainsBadge(urls = emptyList(), modifier = Modifier.testTag("none"))
                }
            }
        }

        compose.onNodeWithTag("five", useUnmergedTree = true).onChildren()
            .filter(hasTestTag("AssetChainsBadge_chain_icon")).assertCountEquals(5)
        compose.onAllNodesWithTag("AssetChainsBadge_chains_extra", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("none", useUnmergedTree = true).onChildren().assertCountEquals(0)
    }

    @Test
    fun moreThanFiveChainsShowFourIconsAndTheRemainder() {
        compose.setContent {
            FearlessAppTheme { AssetChainsBadge(urls = List(7) { "" }) }
        }

        compose.onAllNodesWithTag("AssetChainsBadge_chain_icon", useUnmergedTree = true).assertCountEquals(4)
        compose.onNodeWithTag("AssetChainsBadge_chains_extra", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+3", useUnmergedTree = true).assertExists()
    }
}
