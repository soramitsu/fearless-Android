package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.withNoFontPadding

@Composable
fun TextFieldHint(
    state: String?,
    cursorBrush: Brush = SolidColor(white),
    onInput: (String) -> Unit,
    Hint: @Composable () -> Unit
) {
    var focusedState by remember { mutableStateOf(false) }
    Box(Modifier.height(32.dp)) {
        if (!focusedState && state.isNullOrEmpty()) {
            Box(Modifier.align(Alignment.CenterStart)) {
                Hint()
            }
        }

        BasicTextField(
            value = state.orEmpty(),
            onValueChange = onInput,
            textStyle = MaterialTheme.customTypography.body1.copy(textAlign = TextAlign.Start, background = Color.Unspecified),
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.None),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterStart)
                .onFocusChanged {
                    focusedState = it.isFocused
                },
            cursorBrush = cursorBrush
        )
    }
}

@Preview
@Composable
private fun PreviewTextFieldHint() {
    Box(Modifier.background(Color.Black)) {
        TextFieldHint(
            state = "",
            cursorBrush = SolidColor(colorAccentDark),
            onInput = { },
            Hint = { B1(text = "Public address".withNoFontPadding()) }
        )
    }
}
