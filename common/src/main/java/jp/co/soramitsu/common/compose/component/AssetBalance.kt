package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    val transferableBalance: String,
    val address: String,
    val isInfoEnabled: Boolean = false,
    val changeViewState: ChangeBalanceViewState
)

@Composable
fun AssetBalance(
    state: AssetBalanceViewState,
    onAddressClick: () -> Unit,
    onBalanceClick: () -> Unit = emptyClick
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.changeViewState.fiatChange.isNotEmpty()) {
            ChangeBalance(state.changeViewState)
        }
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .testTag("balance_fiat")
                .clickableWithNoIndication(onBalanceClick)
        ) {
            H1(text = state.transferableBalance)
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

@Composable
fun AssetBalanceShimmer() {
    Shimmer(
        Modifier
            .height(12.dp)
            .padding(horizontal = 120.dp)
    )
    MarginVertical(margin = 10.dp)
    Shimmer(
        Modifier
            .height(26.dp)
            .padding(horizontal = 93.dp)
    )
    MarginVertical(margin = 21.dp)
    Shimmer(
        Modifier
            .height(12.dp)
            .padding(horizontal = 133.dp)
    )
}

@Preview
@Composable
private fun PreviewAssetBalance() {
    val percentChange = "+5.67%"
    val assetTransferableBalance = "44400.3"
    val assetTransferableBalanceFiat = "$2345.32"
    val address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf"

    val state = AssetBalanceViewState(
        transferableBalance = assetTransferableBalance,
        address = address,
        isInfoEnabled = true,
        changeViewState = ChangeBalanceViewState(
            percentChange = percentChange,
            fiatChange = assetTransferableBalanceFiat
        )
    )

    FearlessTheme {
        Column {
            AssetBalance(
                state = state,
                onAddressClick = {},
                onBalanceClick = {}
            )
            MarginVertical(margin = 16.dp)
            AssetBalanceShimmer()
        }
    }
}
