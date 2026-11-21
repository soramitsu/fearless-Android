package jp.co.soramitsu.common.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextFieldHintTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hintShowsUntilTheFieldIsFocusedAndTypingIsReported() {
        val inputs = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                TextFieldHint(state = null, onInput = { inputs += it }, Hint = { B1(text = "Type a name") })
            }
        }

        compose.onNodeWithText("Type a name", useUnmergedTree = true).assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        compose.waitForIdle()
        // A focused field hides its hint.
        compose.onAllNodesWithText("Type a name", useUnmergedTree = true).assertCountEquals(0)
        field.performTextReplacement("Alice")
        compose.waitForIdle()

        assertEquals(listOf("Alice"), inputs)
    }

    @Test
    fun previewShowsItsHint() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.TextFieldHintKt", "PreviewTextFieldHint") }
        }

        compose.onNodeWithText("Public address", useUnmergedTree = true).assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
