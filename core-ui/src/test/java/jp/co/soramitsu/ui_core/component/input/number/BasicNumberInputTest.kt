package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class BasicNumberInputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun normalizesCommaAndRejectsInvalidOrExcessPrecision() {
        val values = mutableListOf<BigDecimal>()
        compose.setContent {
            MaterialTheme { BasicNumberInput(precision = 2, onValueChanged = { values += it }) }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("12,34")
        field.assertTextEquals("12.34")
        field.performTextReplacement("12.345")
        field.assertTextEquals("12.34")
        field.performTextReplacement("-1")
        field.assertTextEquals("12.34")
        field.performTextReplacement("abc")
        field.assertTextEquals("12.34")
        assertEquals(listOf(BigDecimal("12.34")), values)
    }

    @Test
    fun clearingEmitsZeroAndDecimalSeparatorWaitsForDigits() {
        val values = mutableListOf<BigDecimal>()
        compose.setContent {
            MaterialTheme { BasicNumberInput(initial = BigDecimal.TEN, onValueChanged = { values += it }) }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("")
        field.assertTextEquals("")
        assertEquals(listOf(BigDecimal.ZERO), values)
        field.performTextReplacement(".")
        field.assertTextEquals(".")
        assertEquals(1, values.size)
        field.performTextReplacement(".5")
        field.assertTextEquals(".5")
        assertEquals(BigDecimal("0.5"), values.last())
    }

    @Test
    fun externalAmountUpdatesStripTrailingZerosWithoutEmittingUserEdits() {
        val amount = mutableStateOf(BigDecimal("12.300"))
        val values = mutableListOf<BigDecimal>()
        compose.setContent {
            MaterialTheme { BasicNumberInput(initial = amount.value, onValueChanged = { values += it }) }
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("12.3")
        compose.runOnIdle { amount.value = BigDecimal("4.5000") }
        compose.onNode(hasSetTextAction()).assertTextEquals("4.5")
        assertTrue(values.isEmpty())
    }

    @Test
    fun keyboardDoneReachesCallerAndFocusIsReported() {
        var done = 0
        val focus = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme { BasicNumberInput(onKeyboardDone = { done++ }, onFocusChanged = { focus += it }) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("1")
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, done)
        assertTrue(focus.contains(true))
    }

    @Test
    fun disabledInputExposesItsValueAndDisabledSemantics() {
        compose.setContent { MaterialTheme { BasicNumberInput(enabled = false, initial = BigDecimal("7.00")) } }
        compose.onNodeWithText("7").assertIsNotEnabled().assertTextEquals("7")
    }
}
