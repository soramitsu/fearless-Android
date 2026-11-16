package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickInputTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun quickAmountsArePercentagesOfTheBalance() {
        assertEquals(listOf("75%", "50%", "25%"), QuickAmountInput.entries.map { it.label })
        assertEquals(listOf(0.75, 0.5, 0.25), QuickAmountInput.entries.map { it.value })
    }

    @Test
    fun tappingAPercentageOrDoneReportsIt() {
        val amounts = mutableListOf<Double>()
        var done = 0
        compose.setContent {
            FearlessAppTheme {
                QuickInput(onQuickAmountInput = { amounts += it }, onDoneClick = { done++ })
            }
        }

        compose.onNodeWithText("50%").performClick()
        compose.onNodeWithText("25%").performClick()
        compose.onNodeWithText(context.getString(R.string.common_done)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(0.5, 0.25), amounts)
        assertEquals(1, done)
    }

    @Test
    fun previewOffersMax() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.QuickInputKt", "QuickInputPreview") }
        }

        compose.onNodeWithText("MAX").assertIsDisplayed()
        compose.onNodeWithText("75%").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
