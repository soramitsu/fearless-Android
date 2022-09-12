package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography

data class ChangeBalanceViewState(
    val percentChange: String,
    val fiatChange: String
)

@Composable
fun ChangeBalance(
    state: ChangeBalanceViewState
) {
    val balanceChangeStatusColor = if (state.percentChange.startsWith("+")) {
        MaterialTheme.customColors.greenText
    } else {
        MaterialTheme.customColors.red
    }

    Row {
        Text(
            text = state.percentChange,
            style = MaterialTheme.customTypography.body1.copy(
                color = balanceChangeStatusColor
            ),
            modifier = Modifier.testTag("balance_change_percent")
        )
        Text(
            text = "(${state.fiatChange})",
            style = MaterialTheme.customTypography.body1.copy(color = black2),
            modifier = Modifier
                .padding(start = 4.dp)
                .testTag("balance_change_fiat")
        )
    }
}

@Preview
@Composable
private fun ChangeBalancePreview() {
    val percentChange = "+5.67%"
    val assetBalanceFiat = "$2345.32"

    FearlessTheme {
        ChangeBalance(
            state = ChangeBalanceViewState(
                percentChange = percentChange,
                fiatChange = assetBalanceFiat
            )
        )
    }
}
