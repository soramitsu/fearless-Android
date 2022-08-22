package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography

data class AssetBalanceViewState(
    val balance: String,
    val assetSymbol: String,
    val address: String,
    val onAddressClick: () -> Unit,
    val changeViewState: ChangeViewState
)

data class ChangeViewState(
    val percentChange: String,
    val fiatChange: String
)

@Composable
fun AssetBalance(
    state: AssetBalanceViewState
) {
    val balanceChangeStatusColor = if (state.changeViewState.percentChange.startsWith("+")) {
        MaterialTheme.customColors.greenText
    } else {
        MaterialTheme.customColors.red
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Text(
                text = state.changeViewState.percentChange,
                style = MaterialTheme.customTypography.body1.copy(
                    color = balanceChangeStatusColor
                )
            )
            Text(
                text = "(${state.changeViewState.fiatChange})",
                style = MaterialTheme.customTypography.body1,
                modifier = Modifier
                    .alpha(0.64f)
                    .padding(start = 4.dp)
            )
        }
        Row {
            Text(
                text = state.assetSymbol + state.balance,
                style = MaterialTheme.customTypography.header1
            )
        }
        Address(address = state.address, onClick = state.onAddressClick)
    }
}

@Preview
@Composable
fun PreviewAssetBalance() {
    val assetSymbol = "$"
    val percentChange = "+5.67%"
    val assetBalance = "44400.3"
    val assetBalanceFiat = "$2345.32"
    val address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf"

    val state = AssetBalanceViewState(
        balance = assetBalance,
        assetSymbol = assetSymbol,
        address = address,
        onAddressClick = {},
        changeViewState = ChangeViewState(
            percentChange = percentChange,
            fiatChange = assetBalanceFiat
        )
    )

    FearlessTheme {
        AssetBalance(state)
    }
}
