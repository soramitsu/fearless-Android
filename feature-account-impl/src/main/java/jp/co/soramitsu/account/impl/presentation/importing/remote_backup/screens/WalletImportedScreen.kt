package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.theme.gray2

data class WalletImportedState(
    val wallet: EncryptedBackupAccount?
) : ImportRemoteWalletState

interface WalletImportedCallback {

    fun onBackClick()

    fun onContinueClick()

    fun onImportMore()
}

@Composable
internal fun WalletImportedScreen(
    state: WalletImportedState,
    callback: WalletImportedCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.import_remote_wallet_title_password),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        MarginVertical(margin = 24.dp)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            MarginVertical(margin = 16.dp)
            WalletItem(
                state = CompactWalletItemViewState(title = state.wallet?.name.orEmpty()),
                onSelected = {}
            )
            MarginVertical(margin = 16.dp)
            B0(
                text = stringResource(R.string.import_remote_wallet_subtitle_password),
                textAlign = TextAlign.Center,
                color = gray2
            )
            MarginVertical(margin = 16.dp)
        }

        Spacer(modifier = Modifier.weight(1f))
        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = ButtonViewState(
                text = stringResource(R.string.common_continue),
                enabled = true
            ),
            onClick = callback::onContinueClick
        )
        MarginVertical(8.dp)
        GrayButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(R.string.import_remote_wallet_btn_import_more),
            onClick = callback::onImportMore
        )
        MarginVertical(12.dp)
    }
}
