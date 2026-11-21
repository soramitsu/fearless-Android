package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.os.Process
import android.os.SystemClock
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.NetworkScanState

/**
 * What the sync dial reads (brand kit SPEC v4 part 2): of the networks this wallet has started to read
 * balances from since the sync began, how many answered and how many gave up. Only real events count:
 * a network is in the sync once its balance loader reported a scan start at or after [epochMillis]
 * (`NetworkScanStateStore.scanStarted`), done once it reported a success after that start, failed once
 * it reported a failure. Nothing is estimated; a network the app has not started reading is not
 * counted, so the total can grow while networks start.
 */
data class WalletSyncCounts(val done: Int, val failed: Int, val total: Int) {
    val outstanding: Int get() = total - done - failed

    companion object {
        val None = WalletSyncCounts(0, 0, 0)
    }
}

internal fun walletSyncCounts(
    states: Map<NetworkScanKey, NetworkScanState>,
    walletId: Long?,
    chainFilter: String?,
    epochMillis: Long
): WalletSyncCounts {
    if (walletId == null) return WalletSyncCounts.None
    var done = 0
    var failed = 0
    var total = 0
    for ((key, state) in states) {
        if (key.walletId != walletId || (chainFilter != null && key.chainId != chainFilter)) continue
        val attempt = state.lastAttemptMillis ?: continue
        if (attempt < epochMillis) continue
        total++
        when {
            state.isStale -> failed++
            (state.lastSuccessMillis ?: Long.MIN_VALUE) >= attempt -> done++
        }
    }
    return WalletSyncCounts(done, failed, total)
}

/**
 * When this process started, on the wall clock the scan states use: the sync that began with the app.
 * Off a device (JVM tests) it is now.
 */
internal fun processStartWallMillis(): Long {
    val now = System.currentTimeMillis()
    return runCatching { now - (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()) }.getOrDefault(now)
}
