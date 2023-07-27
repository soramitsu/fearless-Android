package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50

data class RemoteWalletListState(
    val wallets: List<BackupAccountMeta>? = null
) : ImportRemoteWalletState

interface RemoteWalletListCallback {

    fun onCreateNewWallet()

    fun onContinueClick()

    fun onWalletSelected(backupAccount: BackupAccountMeta)

    fun onWalletLongClick(backupAccount: BackupAccountMeta)

    fun onBackClick()

    fun loadRemoteWallets()
}

@Composable
internal fun RemoteWalletListScreen(
    state: RemoteWalletListState,
    callback: RemoteWalletListCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LaunchedEffect(Unit) {
            callback.loadRemoteWallets()
        }

        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.import_remote_wallet_title_wallets),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        MarginVertical(margin = 8.dp)
        when {
            state.wallets == null -> {}
            state.wallets.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyResultContent()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.wallets) { wallet ->
                        WalletItem(
                            state = CompactWalletItemViewState(
                                title = wallet.name
                            ),
                            onSelected = {
                                callback.onWalletSelected(wallet)
                            },
                            onLongClick = {
                                callback.onWalletLongClick(wallet)
                            }
                        )
                    }
                }
            }
        }

        AccentButton(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .fillMaxWidth(),
            text = stringResource(R.string.import_remote_wallet_btn_create_wallet),
            enabled = true,
            onClick = callback::onCreateNewWallet
        )
        MarginVertical(12.dp)
    }
}

@Composable
fun EmptyResultContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.import_wallets_not_found),
            color = white50
        )
    }
}

@Preview
@Composable
private fun PreviewRemoteWalletListScreen() {
    FearlessAppTheme {
        RemoteWalletListScreen(
            state = RemoteWalletListState(wallets = emptyList()),
            callback = object : RemoteWalletListCallback {
                override fun onCreateNewWallet() {}
                override fun onContinueClick() {}
                override fun onWalletSelected(backupAccount: BackupAccountMeta) {}
                override fun onWalletLongClick(backupAccount: BackupAccountMeta) {}
                override fun onBackClick() {}
                override fun loadRemoteWallets() {}
            }
        )
    }
}
