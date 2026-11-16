package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
class InputWithHintTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyEditableInputShowsHintAndReportsTyping() {
        val inputs = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                InputWithHint(state = "", onInput = { inputs += it }, Hint = { B1(text = "Public address") })
            }
        }

        compose.onNodeWithText("Public address", useUnmergedTree = true).assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        field.assertIsEnabled().performClick()
        field.performTextReplacement("5Grw")
        compose.waitForIdle()

        assertEquals(listOf("5Grw"), inputs)
    }

    @Test
    fun filledInputHidesHintAndReadOnlyInputShortensTheAddress() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    InputWithHint(state = "editable value", onInput = {}, Hint = { B1(text = "Hidden hint") })
                    InputWithHint(
                        state = "0xabcdefabcdefabcdefabcdef12345678",
                        editable = false,
                        onInput = {},
                        Hint = { B1(text = "Hidden hint") }
                    )
                }
            }
        }

        compose.onAllNodesWithText("Hidden hint", useUnmergedTree = true).assertCountEquals(0)
        compose.onNode(hasText("editable value")).assertIsEnabled()
        compose.onNode(hasText("0xabcdef...12345678")).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun previewShowsItsHint() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.InputWithHintKt", "PreviewInputWithHint") }
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
