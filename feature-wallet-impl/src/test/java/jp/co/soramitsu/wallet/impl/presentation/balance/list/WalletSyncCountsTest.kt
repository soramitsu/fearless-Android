package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.theme.fw.FwSyncPhase
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.NetworkScanState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sync dial counts only real scan events since the sync began (brand kit SPEC v4 part 2, "Honest"). */
class WalletSyncCountsTest {
    private val epoch = 1_000L
    private fun scan(chain: String, attempt: Long?, success: Long? = null, stale: Boolean = false, wallet: Long = 7L) =
        NetworkScanKey(wallet, chain) to NetworkScanState(chain, attempt, success, stale, if (stale) "down" else null, AssetDiscoveryCoverage.Complete)

    @Test fun countsStartedAnsweredAndFailedNetworksOfThisWalletSinceTheSyncBegan() {
        val states = mapOf(
            scan("a", attempt = 1_100, success = 1_200),          // answered
            scan("b", attempt = 1_100),                             // outstanding
            scan("c", attempt = 1_150, stale = true),               // gave up
            scan("d", attempt = 900, success = 950),                // before this sync: not counted
            scan("e", attempt = 1_300, success = 1_200),            // started again after its last answer: outstanding
            scan("f", attempt = null, success = 2_000),             // never started: not counted
            scan("a", attempt = 1_100, success = 1_200, wallet = 8) // another wallet
        )
        val counts = walletSyncCounts(states, walletId = 7L, chainFilter = null, epochMillis = epoch)
        assertEquals(WalletSyncCounts(done = 1, failed = 1, total = 4), counts)
        assertEquals(2, counts.outstanding)
        assertEquals(WalletSyncCounts(1, 0, 1), walletSyncCounts(states, 7L, chainFilter = "a", epochMillis = epoch))
        assertEquals(WalletSyncCounts.None, walletSyncCounts(states, walletId = null, chainFilter = null, epochMillis = epoch))
    }

    @Test fun dialStateSyncsWhileANetworkIsOutstanding_andSettlesWithFailuresShownNotAlarmed() {
        val loaded = WalletState.default.copy(assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(emptyList()), isHideVisible = true))
        val syncing = walletSyncState(loaded.copy(syncCounts = WalletSyncCounts(9, 0, 14)))
        assertEquals(FwSyncPhase.Running, syncing.phase)
        assertEquals(9, syncing.done)
        assertEquals(14, syncing.total)
        val settled = walletSyncState(loaded.copy(syncCounts = WalletSyncCounts(12, 2, 14)))
        assertEquals(FwSyncPhase.Done, settled.phase)
        assertEquals(2, settled.failed)
        // Only busy or idle known (no counts): the existing loading state drives it, counts stay zero.
        val busy = walletSyncState(WalletState.default)
        assertEquals(FwSyncPhase.Running, busy.phase)
        assertEquals(0, busy.total)
    }
}
