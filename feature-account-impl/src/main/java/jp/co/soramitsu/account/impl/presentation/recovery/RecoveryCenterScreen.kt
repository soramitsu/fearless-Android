package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryTextButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R

@Composable
fun RecoveryCenterScreen(
    state: WalletRecoveryInventory.State,
    back: () -> Unit,
    restore: (RecoveryTarget, ImportMode) -> Unit,
    importCorrected: (RecoveryTarget) -> Unit,
    exportStored: () -> Unit,
    backupWallet: (Long, String?) -> Unit,
    archivedData: () -> Unit = {},
    pinUnreadable: Boolean = false,
    resetPin: () -> Unit = {},
    previousSecureStorage: () -> Unit = {},
    savedEndpoints: () -> Unit = {}
) {
    FwRecoveryAppearance {
        val restyled = FwRestyle.isEnabled(FwSurface.Recovery)
        val background = if (restyled) Modifier.background(LocalFwColors.current.bgBase) else Modifier
        Column(Modifier.fillMaxSize().then(background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (restyled) {
                FwRecoveryTextButton(stringResource(R.string.wallet_recovery_back), back)
            } else {
                TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ux_back)) }
            }
            Text(stringResource(R.string.wallet_recovery_center), style = MaterialTheme.typography.h5, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.wallet_recovery_center_intro))
            if (pinUnreadable) {
                Text(stringResource(R.string.pin_recovery_view_only_intro))
                Action(R.string.pin_recovery_reset, resetPin)
            }
            if (state.loading) CircularProgressIndicator()
            if (state.readFailed) Text(stringResource(R.string.wallet_recovery_inventory_failed))
            state.wallets.forEach { wallet ->
                if (wallet.items.isNotEmpty() || wallet.advisories.isNotEmpty() || wallet.readFailed) {
                    Divider()
                    Text(wallet.name, style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
                    if (wallet.readFailed) Text(stringResource(R.string.wallet_recovery_inventory_failed))
                    wallet.items.forEach { item ->
                        Text(scopeLabel(item.target.scope()), style = MaterialTheme.typography.subtitle1)
                        Text(stringResource(when (item.kind) {
                            WalletRecoveryKind.IDENTITY_MISMATCH -> R.string.wallet_recovery_identity_issue
                            WalletRecoveryKind.MISSING_KEY -> R.string.wallet_recovery_missing_key
                            WalletRecoveryKind.QUARANTINED -> R.string.wallet_recovery_unusable_key
                        }))
                        if (item.kind == WalletRecoveryKind.IDENTITY_MISMATCH) {
                            Action(R.string.wallet_recovery_import_corrected) { importCorrected(item.target) }
                        } else {
                            Action(R.string.wallet_recovery_use_phrase) { restore(item.target, ImportMode.MnemonicPhrase) }
                            if (item.target.scope() != WalletSecretScope.Ton) {
                                Action(R.string.wallet_recovery_use_seed) { restore(item.target, ImportMode.RawSeed) }
                                Action(R.string.wallet_recovery_use_json) { restore(item.target, ImportMode.Json) }
                            }
                        }
                    }
                    wallet.advisories.forEach { advisory ->
                        Text(scopeLabel(advisory.scope), style = MaterialTheme.typography.subtitle1)
                        Text(stringResource(if (advisory.asTyped) R.string.wallet_recovery_as_typed else R.string.wallet_recovery_backup_advisory))
                        Action(R.string.wallet_recovery_backup_key) { backupWallet(wallet.id, advisory.chainId) }
                    }
                }
            }
            if (!state.loading && !state.readFailed && state.wallets.none { it.items.isNotEmpty() || it.advisories.isNotEmpty() || it.readFailed }) {
                Text(stringResource(R.string.wallet_recovery_no_attention))
            }
            Divider()
            Text(stringResource(R.string.wallet_recovery_retained), style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.wallet_recovery_retained_intro))
            Action(R.string.wallet_keyloss_retry, previousSecureStorage)
            Action(R.string.saved_endpoints_title, savedEndpoints)
            Action(R.string.wallet_archive_open, archivedData)
            Action(R.string.wallet_emergency_open, exportStored)
            Action(R.string.wallet_recovery_keep_view_only, back)
        }
    }
}

@Composable
private fun Action(label: Int, action: () -> Unit) {
    if (FwRestyle.isEnabled(FwSurface.Recovery)) {
        FwRecoveryButton(stringResource(label), action)
        return
    }
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
}

@Composable
private fun scopeLabel(scope: WalletSecretScope): String = stringResource(when (scope) {
    WalletSecretScope.Substrate -> R.string.wallet_emergency_kind_substrate
    WalletSecretScope.Ethereum -> R.string.wallet_emergency_kind_ethereum
    WalletSecretScope.Ton -> R.string.wallet_emergency_kind_ton
    is WalletSecretScope.ChainAccount -> R.string.wallet_emergency_kind_network
}) + if (scope is WalletSecretScope.ChainAccount) "\n0x${scope.accountIdHex}" else ""
