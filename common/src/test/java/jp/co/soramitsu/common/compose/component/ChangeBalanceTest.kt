package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChangeBalanceTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun risingAndFallingChangesShowPercentAndFiat() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ChangeBalance(ChangeBalanceViewState(percentChange = "+5.67%", fiatChange = "$2,345.32", percentFiatChange = "+$1.12"))
                    ChangeBalance(ChangeBalanceViewState(percentChange = "-1.20%", fiatChange = "$10.00"))
                }
            }
        }

        val percents = compose.onAllNodesWithTag("balance_change_percent", useUnmergedTree = true).assertCountEquals(2)
        percents[0].assertTextEquals("+5.67%")
        percents[1].assertTextEquals("-1.20%")
        compose.onNodeWithTag("fiat_balance_change_percent", useUnmergedTree = true).assertTextEquals("+$1.12")
        val fiat = compose.onAllNodesWithTag("balance_change_fiat", useUnmergedTree = true)
        fiat[0].assertTextEquals("($2,345.32)")
        fiat[1].assertTextEquals("($10.00)")
    }

    @Test
    fun previewShowsARise() {
        compose.setContent { RenderPreview("jp.co.soramitsu.common.compose.component.ChangeBalanceKt", "ChangeBalancePreview") }

        compose.onNodeWithTag("balance_change_percent", useUnmergedTree = true).assertTextEquals("+5.67%")
        compose.onNodeWithTag("balance_change_fiat", useUnmergedTree = true).assertTextEquals("($2345.32)")
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
