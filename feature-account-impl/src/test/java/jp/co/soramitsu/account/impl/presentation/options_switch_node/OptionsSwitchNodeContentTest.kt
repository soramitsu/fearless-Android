package jp.co.soramitsu.account.impl.presentation.options_switch_node

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OptionsSwitchNodeContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun namesTheChainAndSwitchesItsNode() {
        val calls = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                OptionsSwitchNodeContent(
                    state = OptionsSwitchNodeScreenViewState(metaId = 2, chainId = "kusama-id", chainName = "Kusama"),
                    onSwitch = { calls += "switch:$it" },
                    dontShowAgain = { chainId, metaId -> calls += "hide:$chainId:$metaId" },
                    onBackClicked = { calls += "close" }
                )
            }
        }

        compose.onNodeWithText(string(R.string.options_switch_node_title, "Kusama")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.switch_node)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("switch:kusama-id", "close"), calls)
    }

    @Test
    fun previewNamesKusama() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.options_switch_node.OptionsSwitchNodeContentKt",
                "OptionsSwitchNodeScreenPreview"
            )
        }

        compose.onNodeWithText(string(R.string.options_switch_node_title, "Kusama")).assertIsDisplayed()
    }
}
