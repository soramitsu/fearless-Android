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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SettingsItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.customColors

data class BackupWalletState(
    val walletItem: WalletItemViewState,
    val isAuthedToGoogle: Boolean,
    val isWalletSavedInGoogle: Boolean,
    val isMnemonicBackupSupported: Boolean,
    val isSeedBackupSupported: Boolean,
    val isJsonBackupSupported: Boolean
) {
    companion object {
        val Empty = BackupWalletState(
            walletItem = WalletItemViewState(
                id = 0,
                balance = null,
                assetSymbol = null,
                changeBalanceViewState = null,
                title = "",
                walletIcon = R.drawable.ic_wallet,
                isSelected = false,
                additionalMetadata = "",
                score = null
            ),
            isAuthedToGoogle = false,
            isWalletSavedInGoogle = false,
            isMnemonicBackupSupported = true,
            isSeedBackupSupported = false,
            isJsonBackupSupported = true
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

    fun onGoogleLoginError(message: String)

    fun onGoogleSignInSuccess()
}

@Composable
internal fun BackupWalletContent(
    state: BackupWalletState,
    isGoogleAvailable: Boolean,
    callback: BackupWalletCallback
) {

    Column {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.export_wallet),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )

        MarginVertical(16.dp)
        WalletItem(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            state = state.walletItem,
            onSelected = {}
        )

        MarginVertical(16.dp)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            if (state.isMnemonicBackupSupported) {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_pass_phrase_24),
                    text = stringResource(R.string.backup_wallet_show_mnemonic_phrase),
                    onClick = callback::onShowMnemonicPhraseClick
                )
                SettingsDivider()
            }
            if (state.isSeedBackupSupported) {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_key_24),
                    text = stringResource(R.string.backup_wallet_show_raw_seed),
                    onClick = callback::onShowRawSeedClick
                )
                SettingsDivider()
            }
            if (state.isJsonBackupSupported) {
                SettingsItem(
                    icon = painterResource(R.drawable.ic_arrow_up_rectangle_24),
                    text = stringResource(R.string.backup_wallet_export_json),
                    onClick = callback::onExportJsonClick
                )
                SettingsDivider()
            }
            if (isGoogleAvailable && state.isAuthedToGoogle) {
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
            }

            MarginVertical(16.dp)
            B2(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.backup_wallet_warning_about_lose_phrase),
                color = MaterialTheme.customColors.colorGreyText
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier
) {
    Divider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.customColors.dividerGray
    )
}

@Preview
@Composable
private fun PreviewBackupWalletContent() {
    FearlessAppTheme {
        BackupWalletContent(
            state = BackupWalletState.Empty,
            isGoogleAvailable = true,
            callback = object : BackupWalletCallback {
                override fun onBackClick() {}
                override fun onShowMnemonicPhraseClick() {}
                override fun onShowRawSeedClick() {}
                override fun onExportJsonClick() {}
                override fun onDeleteGoogleBackupClick() {}
                override fun onGoogleBackupClick() {}
                override fun onGoogleLoginError(message: String) {}
                override fun onGoogleSignInSuccess() {}
            }
        )
    }
}
