package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcStateStore
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReownImportMarkerTest {
    private val now = section("reown")["now"].asLong

    @Test
    fun `the mark says whether the SDK's files must stay, and nothing before the import`() {
        assertNull(ReownImportMarker.read(WcStoredState()))
        assertEquals(false, ReownImportMarker.read(ReownImportMarker.mark(WcStoredState(), keepLegacyFiles = false)))
        assertEquals(true, ReownImportMarker.read(ReownImportMarker.mark(WcStoredState(), keepLegacyFiles = true)))
    }

    @Test
    fun `the mark survives the client's own writes, pruning and a full handled list`() {
        val storage = WcMemoryStorage()
        runBlocking { ReownSessionImporter(ReownFixtureSource(), MemoryImportAttempts(), { now }).importInto(storage) }
        val clock = WcTestClock(millis = (now + 30 * 86_400L) * 1000)
        val store = WcStateStore(storage, clock)

        runBlocking {
            assertTrue(store.load())
            store.mutate { state ->
                val flood = (0 until WcLimits.MAX_HANDLED_ENTRIES + 10).associate { "m:$it" to clock.nowSeconds() + it + 1 }
                state.copy(sessions = state.sessions.drop(1), handled = state.handled + flood + ("m:old" to 1L))
            }
        }
        val reloaded = WcStateStore(storage, clock).also { runBlocking { it.load() } }.state

        assertEquals(false, ReownImportMarker.read(reloaded))
        assertEquals(WcLimits.MAX_HANDLED_ENTRIES, reloaded.handled.size)
    }
}
