package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class WalletItemViewState(
    val id: Long,
    val balance: String? = null,
    val assetSymbol: String? = null,
    val changeBalanceViewState: ChangeBalanceViewState? = null,
    val title: String,
    val walletIcon: Any,
    val isSelected: Boolean,
    val additionalMetadata: String = ""
)

@Composable
fun WalletItem(
    state: WalletItemViewState,
    onOptionsClick: ((WalletItemViewState) -> Unit)? = null,
    onSelected: (WalletItemViewState) -> Unit
) {
    val borderColor = if (state.isSelected) {
        colorAccent
    } else {
        white24
    }

    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithNoIndication { onSelected(state) },
        borderColor = borderColor,
        backgroundColor = black05
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .size(32.dp)
            ) {
                Icon(
                    painter = rememberAsyncImagePainter(model = state.walletIcon),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(6f),
                horizontalAlignment = Alignment.Start
            ) {
                B2(
                    text = state.title,
                    color = gray2
                )
                H4(
                    text = state.assetSymbol.orEmpty() + state.balance.orEmpty()
                )
                state.changeBalanceViewState?.let {
                    ChangeBalance(state = it)
                }
            }
            onOptionsClick?.let { optionsAction ->
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            optionsAction(state)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dots_horizontal_24),
                            tint = Color.Unspecified,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WalletItemPreview() {
    val percentChange = "+5.67%"
    val assetBalanceFiat = "$2345.32"
    val assetBalance = "44400.3"
    val assetSymbol = "$"
    val walletTitle = "My Wallet"
    val isSelected = true

    val changeBalanceViewState = ChangeBalanceViewState(
        percentChange = percentChange,
        fiatChange = assetBalanceFiat
    )

    val state = WalletItemViewState(
        id = 111,
        balance = assetBalance,
        assetSymbol = assetSymbol,
        title = walletTitle,
        walletIcon = R.drawable.ic_wallet,
        isSelected = isSelected,
        changeBalanceViewState = changeBalanceViewState
    )

    FearlessTheme {
        WalletItem(
            state = state,
            onOptionsClick = {},
            onSelected = {}
        )
    }
}
