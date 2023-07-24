package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.gray1
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class TextInputViewState(
    val text: String,
    val hint: String,
    val placeholder: String = "",
    @DrawableRes val endIcon: Int? = null,
    val isActive: Boolean = true,
    val mode: Mode = Mode.Text
) {
    enum class Mode {
        Text, Password
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TextInput(
    state: TextInputViewState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    onInput: (String) -> Unit,
    onFocusChanged: (FocusState) -> Unit = {},
    onEndIconClick: () -> Unit = emptyClick
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
            val visualTransformation = if (state.mode == TextInputViewState.Mode.Password) {
                PasswordVisualTransformation('*')
            } else {
                VisualTransformation.None
            }
            BasicTextField(
                value = state.text,
                enabled = state.isActive,
                onValueChange = onInput,
                textStyle = MaterialTheme.customTypography.body1.copy(color = textColorState),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = when (state.mode) {
                        TextInputViewState.Mode.Text -> KeyboardType.Text
                        TextInputViewState.Mode.Password -> KeyboardType.Password
                    },
                    imeAction = ImeAction.None
                ),
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .background(color = transparent)
                    .onFocusChanged(onFocusChanged)
                    .fillMaxWidth(),
                cursorBrush = SolidColor(colorAccentDark),
                decorationBox = { innerTextField ->
                    TextFieldDefaults.TextFieldDecorationBox(
                        value = state.text,
                        visualTransformation = visualTransformation,
                        innerTextField = innerTextField,
                        placeholder = {
                            B1(
                                text = state.placeholder,
                                color = gray1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = false,
                        enabled = true,
                        contentPadding = PaddingValues(),
                        interactionSource = remember { MutableInteractionSource() }
                    )
                }
            )
            MarginVertical(margin = 4.dp)
        }
        state.endIcon?.let {
            Box(
                modifier = Modifier
                    .clickableWithNoIndication(onEndIconClick)
                    .align(Alignment.CenterEnd)
                    .padding(12.dp)
                    .imePadding()
            ) {
                Image(res = it)
            }
        }
    }
}

@Composable
@Preview
private fun TextInputPreview() {
    val state = TextInputViewState(
        text = "my best pool",
        hint = "Pool name",
        endIcon = R.drawable.ic_close_16_circle
    )
    FearlessTheme {
        Column {
            TextInput(state, onInput = {})
        }
    }
}
