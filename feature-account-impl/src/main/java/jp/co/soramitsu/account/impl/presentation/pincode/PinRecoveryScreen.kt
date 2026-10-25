package jp.co.soramitsu.account.impl.presentation.pincode

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwRecoveryButton
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R

/** Scrollable actions retain their full labels at large accessibility font sizes. */
@Composable
fun PinRecoveryScreen(
    state: PinCodeViewModel.RecoveryState,
    biometric: () -> Unit,
    deviceCredential: () -> Unit,
    verifyBackup: (RecoveryTarget, ImportMode) -> Unit,
    retry: () -> Unit
) {
    FwRecoveryAppearance {
        val restyled = FwRestyle.isEnabled(FwSurface.Recovery)
        val c = LocalFwColors.current
        Column(Modifier.fillMaxSize().background(if (restyled) c.bgBase else Color(0xff11121a)).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.pin_recovery_title), color = if (restyled) c.textPrimary else Color.White,
                style = MaterialTheme.typography.h5, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.pin_recovery_intro), color = if (restyled) c.textPrimary else Color.White)
            if (state.loading) CircularProgressIndicator(color = if (restyled) c.textLink else colorAccent)
            if (state.error) {
                Text(stringResource(R.string.pin_recovery_failed), color = if (restyled) c.textPrimary else Color.White)
                PinRecoveryAction(R.string.pin_recovery_retry, retry)
            } else if (!state.loading) {
                if (state.strongBiometric) PinRecoveryAction(R.string.pin_recovery_biometric, biometric)
                Text(stringResource(R.string.pin_recovery_backup_intro), color = if (restyled) c.textPrimary else Color.White)
                state.choices.forEach { choice ->
                    Divider(color = if (restyled) c.divider else Color.Gray)
                    Text(choice.name, color = if (restyled) c.textPrimary else Color.White, style = MaterialTheme.typography.h6)
                    Text(stringResource(when (val scope = choice.target.scope()) {
                        WalletSecretScope.Substrate -> R.string.wallet_emergency_kind_substrate
                        WalletSecretScope.Ethereum -> R.string.wallet_emergency_kind_ethereum
                        WalletSecretScope.Ton -> R.string.wallet_emergency_kind_ton
                        is WalletSecretScope.ChainAccount -> R.string.wallet_emergency_kind_network
                    }), color = if (restyled) c.textPrimary else Color.White)
                    (choice.target.scope() as? WalletSecretScope.ChainAccount)?.let {
                        Text("0x${it.accountIdHex}", color = if (restyled) c.textPrimary else Color.White)
                    }
                    PinRecoveryAction(R.string.wallet_recovery_use_phrase) { verifyBackup(choice.target, ImportMode.MnemonicPhrase) }
                    if (choice.target.scope() != WalletSecretScope.Ton) {
                        PinRecoveryAction(R.string.wallet_recovery_use_seed) { verifyBackup(choice.target, ImportMode.RawSeed) }
                        PinRecoveryAction(R.string.wallet_recovery_use_json) { verifyBackup(choice.target, ImportMode.Json) }
                    }
                }
                if (state.choices.isEmpty()) Text(stringResource(R.string.pin_recovery_no_identity), color = if (restyled) c.textPrimary else Color.White)
                if (state.deviceCredential) {
                    Divider(color = if (restyled) c.divider else Color.Gray)
                    Text(stringResource(R.string.pin_recovery_view_only_intro), color = if (restyled) c.textPrimary else Color.White)
                    PinRecoveryAction(R.string.pin_recovery_view_only, deviceCredential)
                }
            }
        }
    }
}

@Composable
private fun PinRecoveryAction(label: Int, action: () -> Unit) {
    if (FwRestyle.isEnabled(FwSurface.Recovery)) {
        FwRecoveryButton(stringResource(label), action)
        return
    }
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(label), color = colorAccent)
    }
}
