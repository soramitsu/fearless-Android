package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NumberInputTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inputAcceptsTwoDigitsAndOneDecimalAndNormalisesZeros() {
        val changes = mutableListOf<String>()
        val focus = mutableListOf<FocusState>()
        var value by mutableStateOf("5")
        compose.setContent {
            FearlessAppTheme {
                NumberInput(
                    state = NumberInputState(title = "Slippage", value = value, suffix = "%"),
                    onValueChange = {
                        value = it
                        changes += it
                    },
                    onInputFocusChange = { focus += it }
                )
            }
        }

        compose.onNodeWithText("Slippage").assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextReplacement("12.5")
        field.performTextReplacement("007")
        field.performTextReplacement("000")
        // Three digits and a second decimal place are refused.
        field.performTextReplacement("123")
        field.performTextReplacement("1.25")
        // Clearing the field becomes zero, which is already the last value.
        field.performTextReplacement("")
        compose.waitForIdle()

        assertEquals(listOf("12.5", "7", "0"), changes)
        assertTrue(focus.any { it.hasFocus })
    }

    @Test
    fun warningAndFocusedVariantsShowTheirTitles() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    NumberInput(state = NumberInputState(title = "Too high", value = "60", warning = true))
                    NumberInput(state = NumberInputState(title = "Editing", value = "1.5", decimalPlaces = 2, isFocused = true))
                }
            }
        }

        compose.onNodeWithText("Too high").assertIsDisplayed()
        compose.onNodeWithText("Editing").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
    }

    @Test
    fun previewShowsItsTitle() {
        compose.setContent { RenderPreview("jp.co.soramitsu.common.compose.component.NumberInputKt", "NumberInputPreview") }

        compose.onNodeWithText("Send to").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
