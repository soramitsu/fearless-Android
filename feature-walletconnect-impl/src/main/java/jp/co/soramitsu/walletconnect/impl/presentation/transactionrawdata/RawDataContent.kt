package jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography

data class RawDataViewState(
    val rawData: String
) {
    companion object {
        val default = RawDataViewState(
            rawData = ""
        )
    }
}

interface RawDataScreenInterface {
    fun onClose()
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RawDataContent(state: RawDataViewState, callback: RawDataScreenInterface) {
    BottomSheetScreen {

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.common_transaction_raw_data),
                onNavigationClick = callback::onClose
            )

            MarginVertical(margin = 8.dp)

            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                CompositionLocalProvider(
                    LocalTextInputService provides null
                ) {
                    BasicTextField(
                        value = state.rawData,
                        onValueChange = {},
                        textStyle = MaterialTheme.customTypography.body1,
                        cursorBrush = SolidColor(colorAccentDark),
                        decorationBox = { innerTextField: @Composable () -> Unit ->
                            TextFieldDefaults.TextFieldDecorationBox(
                                value = state.rawData,
                                visualTransformation = VisualTransformation.None,
                                innerTextField = innerTextField,
                                singleLine = false,
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                contentPadding = PaddingValues(0.dp)
                            )
                        })
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .wrapContentHeight()
            ) {
                MarginVertical(margin = 12.dp)
                AccentButton(
                    text = stringResource(id = R.string.common_close),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = callback::onClose
                )

                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun RawDataPreview() {

    val state = RawDataViewState(
        rawData = LoremIpsum(20).values.joinToString(separator = " ") { it }
    )

    val emptyCallback = object : RawDataScreenInterface {
        override fun onClose() {}
    }

    FearlessTheme {
        RawDataContent(
            state = state,
            callback = emptyCallback
        )
    }
}
