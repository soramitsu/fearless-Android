package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.NetworkScanKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScanStateStoreTest {
    @Test
    fun `failure retains last success and marks balance stale`() {
        val store = NetworkScanStateStore(InMemoryPreferences())

        store.scanStarted(1, "solana", AssetDiscoveryCoverage.Complete, attemptedAtMillis = 10)
        store.scanSucceeded(1, "solana", AssetDiscoveryCoverage.Complete, succeededAtMillis = 20)
        store.scanFailed(1, "solana", AssetDiscoveryCoverage.Complete, "endpoint unavailable", failedAtMillis = 30)

        val state = store.states.value.getValue(NetworkScanKey(1, "solana"))
        assertEquals(20L, state.lastSuccessMillis)
        assertEquals(30L, state.lastAttemptMillis)
        assertTrue(state.isStale)
        assertEquals("endpoint unavailable", state.errorMessage)
    }

    @Test
    fun `all wallet network fields restore eagerly after store recreation`() {
        val preferences = InMemoryPreferences()
        NetworkScanStateStore(preferences).apply {
            scanStarted(7, "iroha", AssetDiscoveryCoverage.Complete, attemptedAtMillis = 10)
            scanSucceeded(7, "iroha", AssetDiscoveryCoverage.Complete, succeededAtMillis = 20)
            scanFailed(
                walletId = 7,
                chainId = "iroha",
                coverage = AssetDiscoveryCoverage.CatalogOnly,
                errorMessage = "endpoint unavailable",
                failedAtMillis = 30
            )
            scanStarted(7, "disabled.network", AssetDiscoveryCoverage.Limited, attemptedAtMillis = 15)
            scanSucceeded(7, "disabled.network", AssetDiscoveryCoverage.Limited, succeededAtMillis = 25)
            scanFailed(
                walletId = 7,
                chainId = "disabled.network",
                coverage = AssetDiscoveryCoverage.Limited,
                errorMessage = "network disabled",
                failedAtMillis = 40
            )
        }

        val restoredStore = NetworkScanStateStore(preferences)

        val restored = restoredStore.states.value.getValue(NetworkScanKey(7, "iroha"))
        assertEquals(30L, restored.lastAttemptMillis)
        assertEquals(20L, restored.lastSuccessMillis)
        assertEquals(AssetDiscoveryCoverage.CatalogOnly, restored.coverage)
        assertTrue(restored.isStale)
        assertEquals("endpoint unavailable", restored.errorMessage)

        val disabled = restoredStore.states.value.getValue(NetworkScanKey(7, "disabled.network"))
        assertEquals(40L, disabled.lastAttemptMillis)
        assertEquals(25L, disabled.lastSuccessMillis)
        assertEquals(AssetDiscoveryCoverage.Limited, disabled.coverage)
        assertTrue(disabled.isStale)
        assertEquals("network disabled", disabled.errorMessage)
    }

    @Test
    fun `outer timeout retains coverage established by loader`() {
        val store = NetworkScanStateStore(InMemoryPreferences())
        store.scanStarted(9, "bitcoin", AssetDiscoveryCoverage.Complete, attemptedAtMillis = 10)

        store.scanFailedPreservingCoverage(
            walletId = 9,
            chainId = "bitcoin",
            fallbackCoverage = AssetDiscoveryCoverage.Limited,
            errorMessage = "Asset discovery timed out",
            failedAtMillis = 30
        )

        val state = store.states.value.getValue(NetworkScanKey(9, "bitcoin"))
        assertEquals(AssetDiscoveryCoverage.Complete, state.coverage)
        assertEquals(30L, state.lastAttemptMillis)
        assertTrue(state.isStale)
    }
}
