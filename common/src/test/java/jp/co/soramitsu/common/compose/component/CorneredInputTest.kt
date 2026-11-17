package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun aHintTooLongForTheFieldKeepsToOneLineAndEndsInAnEllipsis() {
        // Twice the text size on a narrow phone: the wallet home's hint wrapped to two lines and outgrew its field.
        val hint = "Search tokens and networks"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                FearlessAppTheme {
                    Box(Modifier.width(320.dp).testTag("field")) {
                        CorneredInput(state = "", hintLabel = hint, textModifier = Modifier.heightIn(min = 48.dp), onInput = {})
                    }
                }
            }
        }

        val layout = layoutOf(hint)
        assertEquals(1, layout.lineCount)
        assertTrue("what does not fit ends in an ellipsis", layout.isLineEllipsized(0))
        // The ellipsis stops short of the field's rounded end.
        val field = compose.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(hint, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("hint $text in field $field", text.right <= field.right - 15.dp)
    }

    @Test
    fun aHintThatFitsIsShownWhole() {
        compose.setContent {
            FearlessAppTheme {
                Box(Modifier.width(320.dp)) { CorneredInput(state = "", hintLabel = "Search", onInput = {}) }
            }
        }

        val layout = layoutOf("Search")
        assertEquals(1, layout.lineCount)
        assertFalse(layout.isLineEllipsized(0))
    }

    private fun layoutOf(hint: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(hint, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
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
