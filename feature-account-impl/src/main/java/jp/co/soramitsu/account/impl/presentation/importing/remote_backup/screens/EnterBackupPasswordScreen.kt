package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem

data class EnterBackupPasswordState(
    val wallet: EncryptedBackupAccount? = null
) : ImportRemoteWalletState

interface EnterBackupPasswordCallback {

    fun onBackClick()

    fun onContinueClick()
}

@Composable
internal fun EnterBackupPasswordScreen(
    state: EnterBackupPasswordState,
    callback: EnterBackupPasswordCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.import_remote_wallet_title),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        MarginVertical(margin = 8.dp)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            WalletItem(
                state = CompactWalletItemViewState(title = state.wallet?.name.orEmpty()),
                onSelected = {}
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = ButtonViewState(
                text = stringResource(R.string.import_remote_wallet_btn_create_wallet),
                enabled = true
            ),
            onClick = { callback.onContinueClick() }
        )
        MarginVertical(12.dp)
    }
}
