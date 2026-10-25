package jp.co.soramitsu.wallet.impl.presentation.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.feature_wallet_impl.R

/** Explicit public status/retry actions; no control can replace the retained payment. */
@Composable
fun NativeSendRecoveryDialog(state: NativeSendRecoveryUiState, close: () -> Unit, check: () -> Unit, retry: () -> Unit, explorer: () -> Unit) {
    val pending = state.snapshot.state == PendingWalletSend.State.Pending
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!state.busy) close() }) {
        androidx.compose.material.Surface(color = backgroundBlack, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .verticalScroll(rememberScrollState()).padding(20.dp)) {
                H3(text = stringResource(R.string.send_recent_payment))
                B2(text = stringResource(if (!state.snapshot.networkAvailable) R.string.send_pending_network_unavailable else when (state.snapshot.state) {
                    PendingWalletSend.State.Pending -> if (state.snapshot.expired) R.string.send_pending_expired else R.string.send_pending_message
                    PendingWalletSend.State.Confirmed -> R.string.send_confirmed_message
                    PendingWalletSend.State.Failed -> R.string.send_failed_message
                }), modifier = Modifier.padding(top = 12.dp))
                state.snapshot.reviewed?.let { review ->
                    val amount = java.math.BigDecimal(review.amountUnits).movePointLeft(review.precision).stripTrailingZeros().toPlainString()
                    val nativeSymbol = if (state.snapshot.kind == PendingWalletSend.Kind.Bitcoin) "BTC" else "SOL"
                    val precision = if (state.snapshot.kind == PendingWalletSend.Kind.Bitcoin) 8 else 9
                    val fee = java.math.BigDecimal(state.snapshot.feeUnits).movePointLeft(precision).stripTrailingZeros().toPlainString()
                    B2(text = stringResource(R.string.common_network) + ": " + review.networkName, modifier = Modifier.padding(top = 12.dp))
                    B2(text = stringResource(R.string.common_amount) + ": " + amount + " " + review.symbol)
                    B2(text = stringResource(R.string.send_to) + ": " + review.recipient)
                    B2(text = stringResource(R.string.common_network_fee) + ": " + fee + " " + nativeSymbol)
                    if (state.snapshot.accountRentUnits != "0") {
                        val rent = java.math.BigDecimal(state.snapshot.accountRentUnits).movePointLeft(precision).stripTrailingZeros().toPlainString()
                        B2(text = stringResource(R.string.send_account_creation) + ": " + rent + " " + nativeSymbol)
                    }
                }
                B2(text = state.snapshot.transactionId, modifier = Modifier.padding(top = 12.dp))
                state.error?.let { B2(text = it, modifier = Modifier.padding(top = 12.dp)) }
                if (state.snapshot.networkAvailable) TextButton(onClick = check, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { B2(text = stringResource(R.string.send_check_status)) }
                if (state.snapshot.networkAvailable && pending && !state.snapshot.expired) TextButton(onClick = retry, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    B2(text = stringResource(R.string.send_retry_same_payment))
                }
                if (state.snapshot.networkAvailable) TextButton(onClick = explorer, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { B2(text = stringResource(R.string.send_view_explorer)) }
                TextButton(onClick = close, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { B2(text = stringResource(R.string.common_close)) }
            }
        }
    }
}
