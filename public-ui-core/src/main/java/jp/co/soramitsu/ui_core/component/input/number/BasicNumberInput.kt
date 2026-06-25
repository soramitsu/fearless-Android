package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal

@Composable
fun BasicNumberInput(
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    textStyle: TextStyle = TextStyle.Default,
    enabled: Boolean = true,
    precision: Int = 8,
    initial: BigDecimal = BigDecimal.ZERO,
    onValueChanged: (BigDecimal) -> Unit = {},
    focusRequester: FocusRequester? = null,
    cursorColor: Color = Color.Unspecified,
    placeholder: @Composable (() -> Unit)? = null,
    onKeyboardDone: () -> Unit = {}
) {
    var text by remember { mutableStateOf(initial.stripTrailingZeros().toPlainString()) }
    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    LaunchedEffect(initial) {
        val next = initial.stripTrailingZeros().toPlainString()
        if (next != text) {
            text = next
        }
    }

    TextField(
        modifier = modifier
            .then(focusModifier)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        value = text,
        onValueChange = { candidate ->
            val normalized = candidate.replace(',', '.')
            if (normalized.isBlank()) {
                text = ""
                onValueChanged(BigDecimal.ZERO)
                return@TextField
            }
            if (!normalized.matches(Regex("""\d*(\.\d{0,$precision})?"""))) {
                return@TextField
            }
            text = normalized
            normalized.toBigDecimalOrNull()?.let(onValueChanged)
        },
        enabled = enabled,
        textStyle = textStyle,
        singleLine = true,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onKeyboardDone() }),
        colors = TextFieldDefaults.textFieldColors(
            cursorColor = cursorColor.takeUnless { it == Color.Unspecified } ?: Color.White,
            backgroundColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}
