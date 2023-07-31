package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
//    activity: Activity,
    state: ImportRemoteWalletState,
    callback: ImportRemoteWalletCallback
) {
    BottomSheetScreen {
        when (state) {
            is RemoteWalletListState -> {
                RemoteWalletListScreen(
//                    activity = activity,
                    state = state,
                    callback = callback
                )
            }
            is EnterBackupPasswordState -> {
                EnterBackupPasswordScreen(
//                    activity = activity,
                    state = state,
                    callback = callback
                )
            }
            is WalletImportedState -> {
                WalletImportedScreen(
//                    activity = activity,
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
        ImportRemoteWalletContent(
            state = RemoteWalletListState(),
            callback = object : ImportRemoteWalletCallback {
                override fun onCreateNewWallet() {}
                override fun onContinueClick() {}
                override fun onWalletSelected(backupAccount: BackupAccountMeta) {}
                override fun onWalletLongClick(backupAccount: BackupAccountMeta) {}
                override fun onBackClick() {}
                override fun loadRemoteWallets() {}
                override fun onPasswordChanged(password: String) {}
                override fun onPasswordVisibilityClick() {}
                override fun onImportMore() {}
            }
        )
    }
}
