package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

data class AmountInputViewState(
    val tokenName: String,
    val tokenImage: String,
    val totalBalance: String,
    val fiatAmount: String?,
    val tokenAmount: String,
    val title: String? = null,
    val isActive: Boolean = true
)

@Composable
fun AmountInput(
    state: AmountInputViewState,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val title = state.title ?: stringResource(id = R.string.common_amount)
                H5(text = title, modifier = Modifier.weight(1f), color = black2)
                state.fiatAmount?.let {
                    B1(text = it, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = black2)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.tokenImage),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(2.dp)
                        .align(CenterVertically)
                )
                MarginHorizontal(margin = 4.dp)
                H3(text = state.tokenName.uppercase(), modifier = Modifier.align(CenterVertically), color = textColorState)
                MarginHorizontal(margin = 8.dp)
                BasicTextField(
                    value = state.tokenAmount,
                    enabled = state.isActive,
                    onValueChange = onInput,
                    textStyle = MaterialTheme.customTypography.header2.copy(textAlign = TextAlign.End, color = textColorState),
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Decimal, imeAction = ImeAction.None),
                    modifier = Modifier
                        .background(color = transparent)
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
        tokenAmount = "0.1"
    )
    FearlessTheme {
        AmountInput(state, onInput = {})
    }
}
