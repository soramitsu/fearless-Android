package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_wallet_impl.R

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SelectWalletContent(
    state: WalletSelectorViewState,
    onWalletSelected: (WalletItemViewState) -> Unit,
    onWalletOptionsClick: (WalletItemViewState) -> Unit,
    addNewWallet: () -> Unit,
    importWallet: () -> Unit,
    onBackClicked: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp
                )
                .fillMaxWidth()
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.common_title_wallet),
                navigationIconResId = R.drawable.ic_arrow_back_24dp,
                onNavigationClick = onBackClicked
            )
            MarginVertical(margin = 20.dp)
            Box(contentAlignment = Alignment.BottomCenter) {
                LazyColumn(
                    modifier = Modifier.padding(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.wallets.map { it.copy(isSelected = it.id == state.selectedWallet?.id) }) { walletItemState ->
                        WalletItem(
                            state = walletItemState,
                            onOptionsClick = onWalletOptionsClick,
                            onSelected = onWalletSelected
                        )
                    }
                    item {
                        MarginVertical(margin = 12.dp)
                    }
                }
                Column(verticalArrangement = Arrangement.Bottom) {
                    AccentButton(
                        text = stringResource(id = R.string.common_add_new_wallet),
                        onClick = addNewWallet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    MarginVertical(margin = 12.dp)
                    GrayButton(
                        text = stringResource(id = R.string.common_import_wallet),
                        onClick = importWallet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    MarginVertical(margin = 12.dp)
                }
            }
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
        walletIcon = R.drawable.ic_wallet,
        isSelected = isSelected,
        changeBalanceViewState = changeBalanceViewState
    )

    FearlessTheme {
        SelectWalletContent(
            state = WalletSelectorViewState(
                wallets = listOf(walletState, walletState, walletState, walletState, walletState, walletState, walletState, walletState),
                selectedWallet = walletState
            ),
            onWalletSelected = {},
            addNewWallet = {},
            importWallet = {},
            onBackClicked = {},
            onWalletOptionsClick = {}
        )
    }
}
