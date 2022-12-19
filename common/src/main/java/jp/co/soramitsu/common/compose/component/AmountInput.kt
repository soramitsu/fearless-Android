package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.ZERO

data class AmountInputViewState(
    val tokenName: String,
    val tokenImage: String,
    val totalBalance: String,
    val fiatAmount: String?,
    val tokenAmount: String,
    val title: String? = null,
    val isActive: Boolean = true,
    val isFocused: Boolean = false,
    val allowAssetChoose: Boolean = false
)

private val bigDecimalRegexPattern = "[0-9]{1,13}(\\.[0-9]*)?".toRegex()
private const val decimalDelimiter = "."

private fun processNewInputState(state: TextFieldValue, previousState: TextFieldValue): TextFieldValue {
    if (state.text == previousState.text) {
        return previousState.copy(selection = state.selection)
    }

    when {
        state.text.all { char -> char == Char.ZERO } -> {
            return TextFieldValue(text = String.ZERO, selection = TextRange(Int.MAX_VALUE))
        }
        state.text.contains(decimalDelimiter).not() && state.text.startsWith(String.ZERO) -> {
            return processNewInputState(TextFieldValue(text = state.text.removePrefix(String.ZERO), selection = TextRange(Int.MAX_VALUE)), previousState)
        }
        state.text.isEmpty() -> {
            return TextFieldValue(text = String.ZERO, selection = TextRange(Int.MAX_VALUE))
        }
        state.text.matches(bigDecimalRegexPattern) -> {
            return TextFieldValue(text = state.text, selection = TextRange(Int.MAX_VALUE))
        }
        else -> {
            return TextFieldValue(text = previousState.text, selection = TextRange(Int.MAX_VALUE))
        }
    }
}

@Composable
fun AmountInput(
    state: AmountInputViewState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    borderColorFocused: Color = Color.Unspecified,
    onInput: (String) -> Unit = {},
    onInputFocusChange: (FocusState) -> Unit = {},
    onTokenClick: () -> Unit = {}
) {
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = state.tokenAmount)) }
    if (textFieldValueState.text != state.tokenAmount || textFieldValueState.text == String.ZERO) {
        textFieldValueState = textFieldValueState.copy(text = state.tokenAmount, selection = TextRange(state.tokenAmount.length))
    }

    val textColorState = when {
        state.tokenAmount == String.ZERO -> {
            black2
        }
        state.isActive -> {
            white
        }
        else -> {
            black2
        }
    }

    val assetColorState = when {
        state.isActive -> {
            white
        }
        else -> {
            black2
        }
    }

    val borderColorState = when {
        !state.isFocused -> borderColor
        borderColorFocused.isUnspecified -> borderColor
        else -> borderColorFocused
    }

    val onAmountInput: (TextFieldValue) -> Unit = remember {
        callback@{
            val processed = processNewInputState(it, textFieldValueState)
            if (processed.text != textFieldValueState.text) {
                onInput(processed.text)
                textFieldValueState = processed
            }
        }
    }

    BackgroundCorneredWithBorder(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = backgroundColor,
        borderColor = borderColorState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                val title = state.title ?: stringResource(id = R.string.common_amount)
                H5(text = title, modifier = Modifier.weight(1f), color = black2)
                state.fiatAmount?.let {
                    B1(text = it, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = black2)
                }
            }

            Row(
                modifier = if (state.allowAssetChoose) {
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTokenClick)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.tokenImage),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(2.dp)
                        .align(CenterVertically)
                )
                MarginHorizontal(margin = 4.dp)
                H3(text = state.tokenName.uppercase(), modifier = Modifier.align(CenterVertically), color = assetColorState)
                MarginHorizontal(margin = 8.dp)
                if (state.allowAssetChoose) {
                    Image(
                        res = R.drawable.ic_arrow_down,
                        modifier = Modifier
                            .align(CenterVertically)
                            .padding(top = 4.dp, end = 4.dp)
                    )
                }
                BasicTextField(
                    value = textFieldValueState,
                    onValueChange = onAmountInput,
                    enabled = state.isActive,
                    textStyle = MaterialTheme.customTypography.header2.copy(textAlign = TextAlign.End, color = textColorState),
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Decimal, imeAction = ImeAction.None),
                    modifier = Modifier
                        .background(color = transparent)
                        .onFocusChanged(onInputFocusChange)
                        .weight(1f),
                    cursorBrush = SolidColor(white)
                )
            }
            MarginVertical(margin = 4.dp)
            B1(text = state.totalBalance, color = black2)
        }
    }
}

@Composable
@Preview
private fun AmountInputPreview() {
    val state = AmountInputViewState(
        tokenName = "KSM",
        tokenImage = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        totalBalance = "Balance: 20.0",
        fiatAmount = "$120.0",
        tokenAmount = "0.1",
        allowAssetChoose = true
    )
    FearlessTheme {
        AmountInput(state)
    }
}
