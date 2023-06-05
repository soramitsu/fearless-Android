package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount

data class WalletImportedState(
    val wallet: EncryptedBackupAccount?
) : ImportRemoteWalletState

interface WalletImportedCallback {

    fun onBackClick()
}

@Composable
internal fun WalletImportedScreen(
    state: WalletImportedState,
    callback: WalletImportedCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
    }
}
