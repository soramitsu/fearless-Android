package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleValueTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleToValueShowsTitleValueAndAdditionalValue() {
        compose.show {
            TitleToValue(state = TitleValueViewState("Total staked", "2.5M KSM", "\$380.94M"), testTag = "staked")
        }

        compose.onNodeWithTag("stakedTitle", useUnmergedTree = true).assertTextEquals("Total staked")
        compose.onNodeWithTag("stakedValue", useUnmergedTree = true).assertTextEquals("2.5M KSM")
        compose.onNodeWithTag("stakedAdditionalValue", useUnmergedTree = true).assertTextEquals("\$380.94M")
        compose.onNodeWithTag("stakedValueShimmer", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun missingValueShowsAShimmerAndNoAdditionalLine() {
        compose.show {
            Column {
                TitleToValue(state = TitleValueViewState("Min stake"), testTag = "min")
                ChangeToValue(state = TitleValueViewState("Monthly", "0.164 KSM", "\$24.92"), testTag = "change")
            }
        }

        compose.onNodeWithTag("minTitle", useUnmergedTree = true).assertTextEquals("Min stake")
        compose.onNodeWithTag("minValueShimmer", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("minValue", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("minAdditionalValue", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("changeTitle", useUnmergedTree = true).assertTextEquals("Monthly")
        compose.onNodeWithTag("changeValue", useUnmergedTree = true).assertTextEquals("0.164 KSM")
    }

    @Test
    fun changeWithoutStateShowsOnlyThePlaceholder() {
        compose.show { ChangeToValue(state = null, testTag = "pending") }

        compose.onNodeWithTag("pendingTitle", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("pendingValue", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun ComposeContentTestRule.show(content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(
                LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = true),
                content = content
            )
        }
    }
}
