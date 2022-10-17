package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class AssetBalanceViewState(
    val balance: String,
    val address: String,
    val isInfoEnabled: Boolean = false,
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
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .testTag("balance_fiat")
                .clickableWithNoIndication {
                    onBalanceClick()
                }
        ) {
            H1(text = state.balance)
            if (state.isInfoEnabled) {
                MarginHorizontal(margin = 5.dp)
                Image(
                    res = R.drawable.ic_info_white_24,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .align(CenterVertically)
                )
            }
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
    val percentChange = "+5.67%"
    val assetBalance = "44400.3"
    val assetBalanceFiat = "$2345.32"
    val address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf"

    val state = AssetBalanceViewState(
        balance = assetBalance,
        address = address,
        isInfoEnabled = true,
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
