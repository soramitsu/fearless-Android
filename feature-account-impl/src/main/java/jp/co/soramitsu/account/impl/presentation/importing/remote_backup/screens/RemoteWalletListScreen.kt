package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem

data class RemoteWalletListState(
    val wallets: List<BackupAccountMeta> = emptyList()
) : ImportRemoteWalletState

interface RemoteWalletListCallback {

    fun onCreateNewWallet()

    fun onContinueClick()

    fun onWalletSelected(backupAccount: BackupAccountMeta)

    fun onBackClick()

    fun loadRemoteWallets(activity: Activity)
}

@Composable
internal fun RemoteWalletListScreen(
    activity: Activity,
    state: RemoteWalletListState,
    callback: RemoteWalletListCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LaunchedEffect(Unit) {
            callback.loadRemoteWallets(activity)
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
                    }
                )
            }
        }

        AccentButton(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            state = ButtonViewState(
                text = stringResource(R.string.import_remote_wallet_btn_create_wallet),
                enabled = true
            ),
            onClick = callback::onCreateNewWallet
        )
        MarginVertical(12.dp)
    }
}
