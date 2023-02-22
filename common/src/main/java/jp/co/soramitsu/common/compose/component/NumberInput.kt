package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.SuffixTransformer
import jp.co.soramitsu.common.utils.ZERO
import jp.co.soramitsu.common.utils.withNoFontPadding

data class NumberInputState(
    val title: String,
    val value: String,
    val suffix: String = "",
    val decimalPlaces: Int = 1,
    val warning: Boolean = false,
    val isFocused: Boolean = false
)

@Composable
fun NumberInput(
    state: NumberInputState,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    onInputFocusChange: (FocusState) -> Unit = {}
) {
    val value by remember(state.value) { derivedStateOf { state.value } }
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
    val textFieldValue = textFieldValueState.copy(text = value)
    var lastTextValue by remember(value) { mutableStateOf(value) }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.isFocused) {
        if (!state.isFocused) {
            focusManager.clearFocus()
        }
    }

    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        borderColor = when {
            state.warning -> warningOrange
            state.isFocused -> colorAccentDark
            else -> white24
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            MarginHorizontal(margin = 8.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                H5(
                    text = state.title.withNoFontPadding(),
                    color = if (state.warning) {
                        warningOrange
                    } else {
                        black2
                    }
                )
                Row {
                    val onAmountInput = rememberProcessedNewInputState(
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = { newTextFieldValueState ->
                            textFieldValueState = newTextFieldValueState

                            val stringChangedSinceLastInvocation = lastTextValue != newTextFieldValueState.text
                            lastTextValue = newTextFieldValueState.text

                            if (stringChangedSinceLastInvocation) {
                                onValueChange(newTextFieldValueState.text)
                            }
                        },
                        decimalPlaces = 1
                    )

                    val onFocusChanged: (FocusState) -> Unit = remember {
                        {
                            if (it.hasFocus) {
                                textFieldValueState = textFieldValueState.copy(selection = TextRange(Int.MAX_VALUE))
                            }
                            onInputFocusChange(it)
                        }
                    }

                    BasicTextField(
                        modifier = Modifier
                            .onFocusChanged(onFocusChanged),
                        value = textFieldValue,
                        onValueChange = onAmountInput,
                        textStyle = MaterialTheme.customTypography.body1.copy(
                            textAlign = TextAlign.Start,
                            background = Color.Unspecified
                        ),
                        maxLines = 1,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.None
                        ),
                        cursorBrush = SolidColor(white),
                        visualTransformation = SuffixTransformer(state.suffix)
                    )
                }
            }
        }
    }
}

private const val decimalDelimiter = "."
private fun getBigDecimalRegexPattern(decimalPlaces: Int): Regex {
    return "[0-9]{1,2}(\\.[0-9]{0,$decimalPlaces})?".toRegex()
}

@Composable
private fun rememberProcessedNewInputState(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    decimalPlaces: Int
): (TextFieldValue) -> Unit {
    val decimalRegexPattern by remember(decimalPlaces) {
        derivedStateOf { getBigDecimalRegexPattern(decimalPlaces) }
    }
    return remember {
        callback@{
            val processed = processNewInputState(it, textFieldValue, decimalRegexPattern)
            if (processed != textFieldValue) {
                onTextFieldValueChange.invoke(processed)
            }
        }
    }
}

private fun processNewInputState(
    state: TextFieldValue,
    previousState: TextFieldValue,
    bigDecimalRegexPattern: Regex
): TextFieldValue {
    if (state.text == previousState.text) {
        return previousState.copy(selection = state.selection)
    }

    when {
        state.text.all { char -> char == Char.ZERO } -> {
            return TextFieldValue(text = String.ZERO, selection = TextRange(Int.MAX_VALUE))
        }
        state.text.contains(decimalDelimiter).not() && state.text.startsWith(String.ZERO) -> {
            return processNewInputState(
                state = TextFieldValue(text = state.text.removePrefix(String.ZERO), selection = state.selection),
                previousState = previousState,
                bigDecimalRegexPattern = bigDecimalRegexPattern
            )
        }
        state.text.isEmpty() -> {
            return TextFieldValue(text = String.ZERO, selection = TextRange(Int.MAX_VALUE))
        }
        state.text.matches(bigDecimalRegexPattern) -> {
            return state
        }
        else -> {
            return previousState
        }
    }
}

@Preview
@Composable
private fun NumberInputPreview() {
    val state = NumberInputState(
        title = "Send to",
        value = "0xsjkdflsdgueroirgfosdifsd;fgoksd;fg;sd845tg849"
    )
    FearlessTheme {
        NumberInput(state)
    }
}
