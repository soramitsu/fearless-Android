package jp.co.soramitsu.common.compose.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableWithNoIndication

private val emptyClick = {}

data class DropDownViewState(
    val text: String?,
    val hint: String,
    val isActive: Boolean = true
)

private data class DropDownColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color
)

@Composable
fun DropDown(
    state: DropDownViewState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = if (state.isActive) {
        DropDownColors(backgroundColor = black05, borderColor = white24, textColor = white)
    } else {
        DropDownColors(backgroundColor = black4, borderColor = transparent, textColor = black2)
    }

    val clickableModifier = if (state.text != null) {
        Modifier.clickableWithNoIndication(onClick)
    } else {
        Modifier
    }
    BackgroundCorneredWithBorder(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
        backgroundColor = colors.backgroundColor,
        borderColor = colors.borderColor
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                H5(text = state.hint, color = black2)
                if (state.text != null) {
                    B1(text = state.text, color = colors.textColor, maxLines = 1)
                } else {
                    MarginVertical(margin = 4.dp)
                    ShimmerB2(modifier = Modifier.width(130.dp))
                }

            }
            if (state.isActive) {
                Image(
                    res = R.drawable.ic_chevron_down_white,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    tint = white
                )
            }
            MarginHorizontal(margin = 16.dp)
        }
    }
}

@Composable
fun InactiveDropDown(text: String?, @StringRes hint: Int) {
    InactiveDropDown(
        DropDownViewState(
            text = text,
            stringResource(id = hint),
            isActive = false
        )
    )
}

@Composable
fun InactiveDropDown(state: DropDownViewState) {
    DropDown(
        state = state.copy(isActive = false),
        onClick = emptyClick
    )
}

@Composable
@Preview
private fun DropDownPreview() {
    val state = DropDownViewState(
        text = "my best pool",
        hint = "Pool name"
    )
    val loadingState = state.copy(text = null)
    FearlessTheme {
        Column {
            DropDown(state, onClick = {})
            InactiveDropDown(text = "Inactive value", hint = R.string.staking_redeem)
            InactiveDropDown(text = null, hint = R.string.staking_redeem)
            DropDown(loadingState, onClick = {})
        }
    }
}
