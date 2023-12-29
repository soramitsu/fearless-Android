package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme

interface ImportRemoteWalletCallback : RemoteWalletListCallback, EnterBackupPasswordCallback, WalletImportedCallback

interface ImportRemoteWalletState

@Composable
internal fun ImportRemoteWalletContent(
    state: ImportRemoteWalletState,
    callback: ImportRemoteWalletCallback
) {
    BottomSheetScreen {
        when (state) {
            is RemoteWalletListState -> {
                RemoteWalletListScreen(
                    state = state,
                    callback = callback
                )
            }
            is EnterBackupPasswordState -> {
                EnterBackupPasswordScreen(
                    state = state,
                    callback = callback
                )
            }
            is WalletImportedState -> {
                WalletImportedScreen(
                    state = state,
                    callback = callback
                )
            }
        }
    }
}

@Preview
@Composable
internal fun PreviewImportRemoteWalletContent() {
    FearlessAppTheme {
        val wallets = (1..3).map { WrappedBackupAccountMeta(BackupAccountMeta("Wallet name_$it", "Address_$it")) }
        ImportRemoteWalletContent(
            state = RemoteWalletListState(wallets),
            callback = object : ImportRemoteWalletCallback {
                override fun onCreateNewWallet() {}
                override fun onContinueClick() {}
                override fun onWalletSelected(backupAccount: WrappedBackupAccountMeta) {}
                override fun onWalletLongClick(backupAccount: WrappedBackupAccountMeta) {}
                override fun onBackClick() {}
                override fun loadRemoteWallets() {}
                override fun onPasswordChanged(password: String) {}
                override fun onPasswordVisibilityClick() {}
                override fun onImportMore() {}
            }
        )
    }
}
