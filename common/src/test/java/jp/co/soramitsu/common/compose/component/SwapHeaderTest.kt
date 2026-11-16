package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwapHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun headerShowsBothAmountsUnderTheSwapTitle() {
        compose.setContent {
            FearlessAppTheme {
                SwapHeader(
                    fromTokenImage = GradientIconState.Local(R.drawable.ic_vector),
                    toTokenImage = GradientIconState.Local(R.drawable.ic_vector),
                    fromTokenAmount = "15 XSTUSD",
                    toTokenAmount = "3,940.362 PSWAP"
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_action_swap)).assertIsDisplayed()
        compose.onNodeWithText("15 XSTUSD").assertIsDisplayed()
        compose.onNodeWithText("3,940.362 PSWAP").assertIsDisplayed()
    }

    @Test
    fun previewShowsItsAmounts() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.SwapHeaderKt", "SwapHeaderPreview") }
        }

        compose.onNodeWithText("15 XTSUD").assertIsDisplayed()
        compose.onNodeWithText("3,940.362 PSWAP").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
