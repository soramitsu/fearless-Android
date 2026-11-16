package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CorneredInputTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptySearchShowsHintAndReportsTyping() {
        val inputs = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                CorneredInput(state = "", hintLabel = "Search assets", onInput = { inputs += it })
            }
        }

        compose.onNodeWithText("Search assets", useUnmergedTree = true).assertIsDisplayed()
        // Nothing to clear yet: the only clickable node is the text field.
        compose.onAllNodes(hasClickAction() and !hasSetTextAction()).assertCountEquals(0)
        compose.onNode(hasSetTextAction()).performTextReplacement("ksm")
        compose.waitForIdle()

        assertEquals(listOf("ksm"), inputs)
    }

    @Test
    fun filledSearchClearsWithTheCloseIcon() {
        val inputs = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    CorneredInput(state = "dot", onInput = { inputs += it })
                    CorneredInput(state = null, onInput = {})
                }
            }
        }

        compose.onNode(hasSetTextAction() and hasText("dot")).assertIsDisplayed()
        compose.onAllNodes(hasClickAction() and !hasSetTextAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf(""), inputs)
    }

    @Test
    fun previewShowsTheThreeVariants() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.CorneredInputKt", "PreviewCorneredInput") }
        }

        compose.onNodeWithText("Hint text", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(3)
        compose.onNode(hasSetTextAction() and hasText("AAAAAA")).assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
