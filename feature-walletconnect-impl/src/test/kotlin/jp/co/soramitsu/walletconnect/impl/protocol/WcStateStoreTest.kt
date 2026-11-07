package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WcStateStoreTest {
    private val clock = WcTestClock()
    private val storage = WcMemoryStorage()

    @Test
    fun `changes are stored and read back after a restart`() = runBlocking {
        val store = WcStateStore(storage, clock)
        assertTrue(store.load())
        store.mutate { it.copy(clientSeed = "ab".repeat(32), pairings = listOf(pairing())) }

        val restarted = WcStateStore(storage, clock)
        assertTrue(restarted.load())

        assertEquals("ab".repeat(32), restarted.state.clientSeed)
        assertEquals(listOf(pairing()), restarted.state.pairings)
    }

    @Test
    fun `a change that does not alter the state writes nothing`() = runBlocking {
        val store = WcStateStore(storage, clock)
        store.load()
        store.mutate { it }

        assertEquals(0, storage.writes)
    }

    @Test
    fun `an empty state removes the stored value`() = runBlocking {
        val store = WcStateStore(storage, clock)
        store.load()
        store.mutate { it.copy(pairings = listOf(pairing())) }
        store.mutate { it.copy(pairings = emptyList()) }

        assertNull(storage.json)
    }

    @Test
    fun `unreadable storage leaves the store unavailable and refuses changes`() = runBlocking {
        storage.json = "sealed"
        storage.unreadable = true
        val store = WcStateStore(storage, clock)

        assertFalse(store.load())
        assertFalse(store.isAvailable)
        assertThrows(WcStorageUnavailableException::class.java) { runBlocking { store.mutate { it } } }
        assertEquals("sealed", storage.json)
    }

    @Test
    fun `corrupt or future state reads as empty`() = runBlocking {
        storage.json = "{not json"
        val store = WcStateStore(storage, clock)
        assertTrue(store.load())
        assertTrue(store.state.pairings.isEmpty())

        storage.json = """{"version":99,"pairings":[]}"""
        assertTrue(store.load())
        assertEquals(WcStoredState.CURRENT_VERSION, store.state.version)
    }

    @Test
    fun `handled entries expire and stay bounded`() = runBlocking {
        val store = WcStateStore(storage, clock)
        store.load()
        val now = clock.nowSeconds()
        val entries = (0 until WcLimits.MAX_HANDLED_ENTRIES + 10).associate { "m:$it" to now + 10 + it } + ("old" to now - 1)
        store.mutate { it.copy(handled = entries, pairings = listOf(pairing())) }

        assertEquals(WcLimits.MAX_HANDLED_ENTRIES, store.state.handled.size)
        assertFalse(store.state.handled.containsKey("old"))
        assertFalse(store.state.handled.containsKey("m:0"))
    }

    @Test
    fun `stored models map to the app's models`() {
        val verify = WcStoredVerify("VALID", "https://a.org", "https://a.org/x", isScam = false).toModel()
        assertEquals("https://a.org", verify.verifiedOrigin)
        assertEquals(co.jp.soramitsu.walletconnect.model.WalletConnectValidation.UNKNOWN, WcStoredVerify("bogus").toModel().validation)
        assertEquals(WcStoredVerify.of(verify), WcStoredVerify("VALID", "https://a.org", "https://a.org/x", false))
        val metadata = WcStoredMetadata("n", "d", "u", listOf("i"), "r")
        assertEquals(metadata, WcStoredMetadata.of(metadata.toModel()))
        val proposalNamespace = WcStoredProposalNamespace(listOf("eip155:1"), listOf("m"), listOf("e"))
        assertEquals(proposalNamespace, WcStoredProposalNamespace.of(proposalNamespace.toModel()))
        val sessionNamespace = WcStoredSessionNamespace(listOf("eip155:1"), listOf("a"), listOf("m"), listOf("e"))
        assertEquals(sessionNamespace, WcStoredSessionNamespace.of(sessionNamespace.toModel()))
    }

    private fun pairing() = WcStoredPairing("t".repeat(64), "k".repeat(64), "irn", clock.nowSeconds() + 300, active = false)
}
