package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun SelectWalletContent(
    state: WalletSelectorViewState,
    onWalletSelected: (WalletItemViewState) -> Unit,
    onWalletOptionsClick: (WalletItemViewState) -> Unit,
    addNewWallet: () -> Unit,
    onBackClicked: () -> Unit,
    onScoreClick: (WalletItemViewState) -> Unit
) {
    // The list keeps the page gutter as content padding, so its cards' light is not cut at its edges.
    val sunlit = FwRestyle.isEnabled(FwSurface.Pickers)
    val gutter = if (sunlit) 20.dp else 16.dp
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = if (sunlit) 0.dp else gutter
                )
                .fillMaxWidth()
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            ToolbarBottomSheet(
                modifier = if (sunlit) Modifier.padding(horizontal = gutter) else Modifier,
                title = stringResource(id = R.string.common_title_wallet),
                navigationIconResId = R.drawable.ic_arrow_back_24dp,
                onNavigationClick = onBackClicked
            )
            MarginVertical(margin = if (sunlit) 12.dp else 20.dp)
            Box(contentAlignment = Alignment.BottomCenter) {
                LazyColumn(
                    modifier = Modifier.padding(bottom = if (sunlit) 62.dp else 50.dp),
                    contentPadding = if (sunlit) PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = 8.dp) else PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(if (sunlit) 12.dp else 8.dp)
                ) {
                    items(state.wallets.map { it.copy(isSelected = it.id == state.selectedWallet?.id) }) { walletItemState ->
                        WalletItem(
                            state = walletItemState,
                            onOptionsClick = onWalletOptionsClick,
                            onSelected = onWalletSelected,
                            onScoreClick = onScoreClick
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
                            .then(if (sunlit) Modifier.padding(horizontal = gutter) else Modifier)
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
            onBackClicked = {},
            onWalletOptionsClick = {},
            onScoreClick = {}
        )
    }
}
