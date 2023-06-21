package jp.co.soramitsu.account.impl.presentation.backup_wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SettingsItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.customColors

data class BackupWalletState(
    val walletItem: WalletItemViewState?,
    val isWalletSavedInGoogle: Boolean,
    val isDeleteWalletEnabled: Boolean
) {
    companion object {
        val Empty = BackupWalletState(
            walletItem = null,
            isWalletSavedInGoogle = false,
            isDeleteWalletEnabled = false
        )
    }
}

interface BackupWalletCallback {

    fun onBackClick()

    fun onShowMnemonicPhraseClick()

    fun onShowRawSeedClick()

    fun onExportJsonClick()

    fun onDeleteGoogleBackupClick()

    fun onGoogleBackupClick()

    fun onDeleteWalletClick()
}

@Composable
internal fun BackupWalletContent(
    state: BackupWalletState,
    callback: BackupWalletCallback
) {
    Column {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.common_backup_wallet),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        if (state.walletItem != null) {
            MarginVertical(16.dp)
            WalletItem(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                state = state.walletItem,
                onSelected = {}
            )
        }
        MarginVertical(16.dp)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            SettingsItem(
                icon = painterResource(R.drawable.ic_pass_phrase_24),
                text = stringResource(R.string.backup_wallet_show_mnemonic_phrase),
                onClick = callback::onShowMnemonicPhraseClick
            )
            SettingsDivider()
            SettingsItem(
                icon = painterResource(R.drawable.ic_key_24),
                text = stringResource(R.string.backup_wallet_show_raw_seed),
                onClick = callback::onShowRawSeedClick
            )
            SettingsDivider()
            SettingsItem(
                icon = painterResource(R.drawable.ic_arrow_up_rectangle_24),
                text = stringResource(R.string.backup_wallet_export_json),
                onClick = callback::onExportJsonClick
            )
            SettingsDivider()
            if (state.isWalletSavedInGoogle) {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_google_24),
                    text = stringResource(R.string.backup_wallet_delete_google_backup),
                    onClick = callback::onDeleteGoogleBackupClick
                )
            } else {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_google_24),
                    text = stringResource(R.string.backup_wallet_backup_to_google),
                    onClick = callback::onGoogleBackupClick
                )
            }
            SettingsDivider()

            MarginVertical(16.dp)
            B2(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.backup_wallet_warning_about_lose_phrase),
                color = MaterialTheme.customColors.colorGreyText
            )

            if (state.isDeleteWalletEnabled) {
                MarginVertical(12.dp)

                SettingsItem(
                    icon = painterResource(R.drawable.ic_sign_out_24),
                    text = stringResource(R.string.common_delete_wallet),
                    onClick = callback::onDeleteWalletClick
                )
                SettingsDivider()
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsDivider(
    modifier: Modifier = Modifier
) {
    Divider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.customColors.dividerGray
    )
}
