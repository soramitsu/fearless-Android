package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24

data class TextInputViewState(
    val text: String,
    val hint: String,
    val isActive: Boolean = true
)

@Composable
fun TextInput(
    state: TextInputViewState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    onInput: (String) -> Unit
) {
    val textColorState = if (state.isActive) {
        white
    } else {
        black2
    }
    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth(),
        backgroundColor = backgroundColor,
        borderColor = borderColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            H5(text = state.hint, color = black2)
            BasicTextField(
                value = state.text,
                enabled = state.isActive,
                onValueChange = onInput,
                textStyle = MaterialTheme.customTypography.body1.copy(color = textColorState),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.None),
                modifier = Modifier
                    .background(color = transparent)
                    .fillMaxWidth(),
                cursorBrush = SolidColor(white)
            )
            MarginVertical(margin = 4.dp)
        }
    }
}

@Composable
@Preview
private fun TextInputPreview() {
    val state = TextInputViewState(
        text = "my best pool",
        hint = "Pool name"
    )
    FearlessTheme {
        Column {
            TextInput(state, onInput = {})
        }
    }
}
