package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters.Filter
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChainSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun statusSelectorIsClickableOnlyWithACallback() {
        var taps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ChainSelector(selectorViewState = ChainSelectorViewState(selectedChainName = "Kusama", selectedChainId = "ksm"), onChangeChainClick = { taps++ })
                    ChainSelector(selectorViewState = ChainSelectorViewState())
                }
            }
        }

        compose.onNodeWithText("Kusama").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText(context.getString(R.string.chain_selection_all_networks)).assertHasNoClickAction()
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test
    fun filterSelectorNamesTheFilterWhenNoChainIsChosen() {
        var taps = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ChainSelector(
                        selectorViewState = ChainSelectorViewStateWithFilters(filterApplied = Filter.All, allowChainSelection = true),
                        onChangeChainClick = { taps++ }
                    )
                    ChainSelector(selectorViewState = ChainSelectorViewStateWithFilters(filterApplied = Filter.Popular), onChangeChainClick = { taps++ })
                    ChainSelector(selectorViewState = ChainSelectorViewStateWithFilters(filterApplied = Filter.Favorite), onChangeChainClick = null)
                    ChainSelector(
                        selectorViewState = ChainSelectorViewStateWithFilters(
                            selectedChainName = "Moonbeam",
                            selectedChainId = "glmr",
                            selectedChainImageUrl = "",
                            allowChainSelection = true
                        ),
                        onChangeChainClick = null
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.chain_selection_all_networks)).assertHasClickAction().performClick()
        // Selection must be allowed as well as handled before the row reacts.
        compose.onNodeWithText(context.getString(R.string.network_management_popular)).assertHasNoClickAction().performClick()
        compose.onNodeWithText(context.getString(R.string.network_managment_favourite)).assertHasNoClickAction()
        compose.onNodeWithText("Moonbeam").assertIsDisplayed().assertHasNoClickAction()
        compose.waitForIdle()

        assertEquals(1, taps)
    }
}
