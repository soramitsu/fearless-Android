package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class AssetBalanceViewState(
    val balance: String,
    val assetSymbol: String,
    val address: String,
    val changeViewState: ChangeBalanceViewState
)

@Composable
fun AssetBalance(
    state: AssetBalanceViewState,
    onAddressClick: () -> Unit,
    onBalanceClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        ChangeBalance(state.changeViewState)
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
        changeViewState = ChangeBalanceViewState(
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
