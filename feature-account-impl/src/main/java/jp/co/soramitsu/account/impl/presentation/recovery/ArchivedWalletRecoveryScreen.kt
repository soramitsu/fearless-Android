package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Surface
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R

@Composable
fun ArchivedWalletRecoveryScreen(
    state: ArchivedWalletRecoveryViewModel.State,
    back: () -> Unit,
    retry: () -> Unit,
    unlock: () -> Unit,
    restore: (ArchivedWalletRecovery.Record, WalletSecretScope) -> Unit,
    purge: (ArchivedWalletRecovery.Record) -> Unit,
    confirm: () -> Unit,
    cancel: () -> Unit,
    export: (ArchivedWalletRecovery.Record) -> Unit,
    closeExport: () -> Unit,
    share: (String) -> Unit
) {
    // The legacy Fragment theme uses transparent surfaces; recovery dialogs must
    // remain opaque and readable over any wallet screen or activity background.
    MaterialTheme(colors = MaterialTheme.colors.copy(
        primary = white, background = backgroundBlack, surface = backgroundBlack,
        onBackground = white, onSurface = white
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = backgroundBlack, contentColor = white) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ArchiveAction(R.string.ux_back, back)
                Text(stringResource(R.string.wallet_recovery_retained), style = MaterialTheme.typography.h5)
                Text(stringResource(R.string.wallet_archive_intro))
                when {
                    state.locked -> ArchiveAction(R.string.wallet_archive_unlock, unlock)
                    state.busy -> CircularProgressIndicator()
                    else -> {
                        if (state.failed) {
                            Text(stringResource(R.string.wallet_recovery_restore_failed))
                            ArchiveAction(R.string.common_try_again, retry)
                        }
                        if (state.records.isEmpty() && !state.failed) Text(stringResource(R.string.wallet_archive_empty))
                        state.records.forEach { record ->
                            Divider()
                            Text(record.key, style = MaterialTheme.typography.caption)
                            record.addresses.forEach { address ->
                                Text(stringResource(when (address.scope) {
                                    WalletSecretScope.Substrate -> R.string.wallet_emergency_kind_substrate
                                    WalletSecretScope.Ethereum -> R.string.wallet_emergency_kind_ethereum
                                    WalletSecretScope.Ton -> R.string.wallet_emergency_kind_ton
                                    is WalletSecretScope.ChainAccount -> R.string.wallet_emergency_kind_network
                                }), style = MaterialTheme.typography.subtitle1)
                                Text(address.text)
                                ArchiveAction(R.string.wallet_recovery_restore) { restore(record, address.scope) }
                            }
                            if (record.readable && record.unprovedParts > 0) Text(stringResource(R.string.wallet_archive_unproved))
                            if (record.readable) ArchiveAction(R.string.wallet_archive_exact_data) { export(record) }
                            else Text(stringResource(R.string.wallet_archive_unreadable))
                            if (record.canPurge) ArchiveAction(R.string.wallet_archive_delete) { purge(record) }
                        }
                    }
                }
            }
            state.confirmation?.let { request ->
                AlertDialog(
                    onDismissRequest = cancel,
                    title = { Text(stringResource(if (request.purgeStep > 0) R.string.wallet_archive_delete else R.string.wallet_recovery_restore)) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(when (request.purgeStep) {
                            1 -> R.string.wallet_archive_delete_first
                            2 -> R.string.wallet_archive_delete_final
                            else -> R.string.wallet_archive_restore_confirm
                        }))
                        request.record.addresses.filter { request.scope == null || it.scope == request.scope }.forEach { Text(it.text) }
                    } },
                    confirmButton = { TextButton(onClick = confirm) { Text(stringResource(when (request.purgeStep) {
                        1 -> R.string.wallet_archive_continue_delete
                        2 -> R.string.wallet_archive_delete
                        else -> R.string.wallet_recovery_restore
                    })) } },
                    dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.common_cancel)) } }
                )
            }
            state.export?.let { data ->
                AlertDialog(onDismissRequest = closeExport,
                    title = { Text(stringResource(R.string.wallet_archive_exact_data)) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.wallet_archive_export_warning))
                        Text(data, style = MaterialTheme.typography.caption)
                    } },
                    confirmButton = { TextButton(onClick = { share(data) }) { Text(stringResource(R.string.wallet_archive_share)) } },
                    dismissButton = { TextButton(onClick = closeExport) { Text(stringResource(R.string.common_close)) } })
            }
        }
    }
}

@Composable
private fun ArchiveAction(label: Int, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
}
