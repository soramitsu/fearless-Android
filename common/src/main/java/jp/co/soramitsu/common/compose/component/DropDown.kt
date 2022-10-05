package jp.co.soramitsu.common.compose.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class DropDownViewState(
    val text: String,
    val hint: String,
    val isActive: Boolean = true
)

@Composable
fun DropDown(
    state: DropDownViewState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    onClick: () -> Unit
) {
    val textColorState = if (state.isActive) {
        white
    } else {
        black2
    }
    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth()
            .clickableWithNoIndication(onClick),
        backgroundColor = backgroundColor,
        borderColor = borderColor
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                H5(text = state.hint, color = black2)
                B1(text = state.text, color = textColorState, maxLines = 1)

            }
            Image(res = R.drawable.ic_chevron_down_white, modifier = Modifier.align(Alignment.CenterVertically), tint = textColorState)
            MarginHorizontal(margin = 16.dp)
        }
    }
}

@Composable
fun InactiveDropDown(text: String, @StringRes hint: Int) {
    DropDown(
        state = DropDownViewState(
            text = text,
            stringResource(id = hint),
            isActive = false
        ),
        onClick = {}
    )
}

@Composable
@Preview
private fun DropDownPreview() {
    val state = DropDownViewState(
        text = "my best pool",
        hint = "Pool name"
    )
    FearlessTheme {
        Column {
            DropDown(state, onClick = {})
            InactiveDropDown(text = "Inactive value", hint = R.string.staking_redeem)
        }
    }
}
