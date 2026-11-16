package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
class ToolbarBottomSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sheetToolbarShowsTitleAndReportsNavigation() {
        var back = 0
        var close = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ToolbarBottomSheet(title = "Select network", onNavigationClick = { back++ })
                    ToolbarBottomSheet(title = null, navigationIconResId = R.drawable.ic_cross_24, onNavigationClick = { close++ })
                }
            }
        }

        compose.onNodeWithText("Select network").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_close)).performClick()
        compose.waitForIdle()

        assertEquals(1, back)
        assertEquals(1, close)
    }

    @Test
    fun previewShowsTitleAndBackButton() {
        compose.setContent { RenderPreview("jp.co.soramitsu.common.compose.component.ToolbarBottomSheetKt", "ToolbarBottomSheetPreview") }

        compose.onNodeWithText("title").assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.ux_back)).assertCountEquals(1)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
