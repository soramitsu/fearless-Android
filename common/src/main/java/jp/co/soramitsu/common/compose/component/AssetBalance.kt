package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class AssetBalanceViewState(
    val balance: String,
    val assetSymbol: String,
    val address: String,
    val changeViewState: ChangeViewState
)

data class ChangeViewState(
    val percentChange: String,
    val fiatChange: String
)

@Composable
fun AssetBalance(
    state: AssetBalanceViewState,
    onAddressClick: () -> Unit,
    onBalanceClick: () -> Unit
) {
    val balanceChangeStatusColor = if (state.changeViewState.percentChange.startsWith("+")) {
        MaterialTheme.customColors.greenText
    } else {
        MaterialTheme.customColors.red
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Text(
                text = state.changeViewState.percentChange,
                style = MaterialTheme.customTypography.body1.copy(
                    color = balanceChangeStatusColor
                ),
                modifier = Modifier.testTag("balance_change_percent")
            )
            Text(
                text = "(${state.changeViewState.fiatChange})",
                style = MaterialTheme.customTypography.body1.copy(color = black2),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag("balance_change_fiat")
            )
        }
        Row {
            Text(
                text = state.assetSymbol + state.balance,
                style = MaterialTheme.customTypography.header1,
                modifier = Modifier
                    .testTag("balance_fiat")
                    .clickableWithNoIndication {
                        onBalanceClick()
                    }
            )
        }
        if (state.address.isNotEmpty()) {
            Address(
                address = state.address,
                onClick = onAddressClick,
                modifier = Modifier.testTag("balance_address")
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAssetBalance() {
    val assetSymbol = "$"
    val percentChange = "+5.67%"
    val assetBalance = "44400.3"
    val assetBalanceFiat = "$2345.32"
    val address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf"

    val state = AssetBalanceViewState(
        balance = assetBalance,
        assetSymbol = assetSymbol,
        address = address,
        changeViewState = ChangeViewState(
            percentChange = percentChange,
            fiatChange = assetBalanceFiat
        )
    )

    FearlessTheme {
        AssetBalance(
            state = state,
            onAddressClick = {},
            onBalanceClick = {}
        )
    }
}
