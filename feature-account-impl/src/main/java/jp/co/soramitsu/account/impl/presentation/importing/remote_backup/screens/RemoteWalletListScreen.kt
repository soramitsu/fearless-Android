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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.labelRes
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletNameItemViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.white50

data class RemoteWalletListState(
    val wallets: List<WrappedBackupAccountMeta>? = null,
    val error: RemoteWalletListError? = null
) : ImportRemoteWalletState

/** Why the Google Drive backups could not be listed; [actionText] is null when retrying cannot help. */
data class RemoteWalletListError(
    val title: String,
    val message: String,
    val actionText: String?
)

interface RemoteWalletListCallback {

    fun onCreateNewWallet()

    fun onContinueClick()

    fun onWalletSelected(backupAccount: WrappedBackupAccountMeta)

    fun onWalletLongClick(backupAccount: WrappedBackupAccountMeta)

    fun onBackClick()

    fun loadRemoteWallets()

    fun onErrorActionClick()

    fun onImportAnotherWayClick()

    fun onUseAnotherGoogleAccountClick()
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
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    RemoteWalletListErrorContent(
                        error = state.error,
                        onActionClick = callback::onErrorActionClick,
                        onImportAnotherWayClick = callback::onImportAnotherWayClick
                    )
                }
            }
            state.wallets == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FearlessProgress()
                }
            }
            state.wallets.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    NoRemoteWalletsContent(
                        onUseAnotherGoogleAccountClick = callback::onUseAnotherGoogleAccountClick,
                        onImportAnotherWayClick = callback::onImportAnotherWayClick
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.wallets) { wallet ->
                        WalletNameItem(
                            state = CompactWalletNameItemViewState(
                                title = wallet.backupMeta.name,
                                subtitle = wallet.origin.labelRes?.let { stringResource(it) }
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

/**
 * Signed in, but this Google account holds no Fearless backups. Someone who backed up with another
 * Google account, or never did, still needs a way to their wallet from here.
 */
@Composable
private fun NoRemoteWalletsContent(
    onUseAnotherGoogleAccountClick: () -> Unit,
    onImportAnotherWayClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )
        H3(text = stringResource(id = R.string.google_backup_none_found_title), textAlign = TextAlign.Center)
        B0(
            text = stringResource(id = R.string.google_backup_none_found_message),
            textAlign = TextAlign.Center,
            color = white50
        )
        GrayButton(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            text = stringResource(R.string.google_backup_use_another_account),
            onClick = onUseAnotherGoogleAccountClick
        )
        GrayButton(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            text = stringResource(R.string.google_backup_import_another_way),
            onClick = onImportAnotherWayClick
        )
    }
}

@Composable
private fun RemoteWalletListErrorContent(
    error: RemoteWalletListError,
    onActionClick: () -> Unit,
    onImportAnotherWayClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )
        H3(text = error.title, textAlign = TextAlign.Center)
        B0(
            text = error.message,
            textAlign = TextAlign.Center,
            color = white50
        )
        error.actionText?.let { actionText ->
            AccentButton(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(),
                text = actionText,
                onClick = onActionClick
            )
        }
        GrayButton(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            text = stringResource(R.string.google_backup_import_another_way),
            onClick = onImportAnotherWayClick
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
                override fun onWalletSelected(backupAccount: WrappedBackupAccountMeta) {}
                override fun onWalletLongClick(backupAccount: WrappedBackupAccountMeta) {}
                override fun onBackClick() {}
                override fun loadRemoteWallets() {}
                override fun onErrorActionClick() {}
                override fun onImportAnotherWayClick() {}
                override fun onUseAnotherGoogleAccountClick() {}
            }
        )
    }
}
