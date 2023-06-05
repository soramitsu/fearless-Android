package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.runtime.Composable
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedCallback
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedScreen
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

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
