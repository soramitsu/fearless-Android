package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.white

data class WalletSelectorViewState(
    val wallets: List<WalletItemViewState>,
    val selectedWallet: WalletItemViewState?
)

@Composable
fun WalletSelectorLight(
    state: WalletSelectorViewState,
    onWalletSelected: (WalletItemViewState) -> Unit,
    onBackClicked: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp
                )
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(3f)
                ) {
                    androidx.compose.material.IconButton(
                        onClick = {
                            onBackClicked()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(backgroundBlurColor)
                            .size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                            tint = white,
                            contentDescription = null
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(4f)
                ) {
                    H4(text = stringResource(id = R.string.common_title_wallet))
                }
            }
            MarginVertical(margin = 20.dp)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.wallets.map { it.copy(isSelected = it.id == state.selectedWallet?.id) }) { walletItemState ->
                    WalletItem(
                        state = walletItemState,
                        onSelected = onWalletSelected
                    )
                }
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun SelectWalletScreenPreview() {
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

    val walletState = WalletItemViewState(
        id = 111,
        balance = assetBalance,
        assetSymbol = assetSymbol,
        title = walletTitle,
        walletIcon = painterResource(id = R.drawable.ic_wallet),
        isSelected = isSelected,
        changeBalanceViewState = changeBalanceViewState
    )

    FearlessTheme {
        WalletSelectorLight(
            state = WalletSelectorViewState(
                wallets = listOf(walletState, walletState),
                selectedWallet = walletState
            ),
            onWalletSelected = {},
            onBackClicked = {},
        )
    }
}
