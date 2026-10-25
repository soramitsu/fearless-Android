package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import org.junit.Assert.*
import org.junit.Test

class WalletSelectionEpochTest {
    @Test fun `presentation invalidation signal advances at both transition boundaries`() {
        val initial = WalletSelectionEpoch.changes.value
        WalletSelectionEpoch.beginChange()
        val beginning = WalletSelectionEpoch.changes.value
        try {
            assertTrue(beginning > initial)
            assertFalse(WalletSelectionEpoch.isCurrent(beginning))
        } finally { WalletSelectionEpoch.endChange() }
        val finished = WalletSelectionEpoch.changes.value
        assertTrue(finished > beginning)
        assertEquals(WalletSelectionEpoch.snapshot(), finished)
        assertFalse(WalletSelectionEpoch.isCurrent(initial))
    }

    @Test fun `conditional selection admission skips stale work but preserves admitted exceptions`() {
        val epoch = WalletSelectionEpoch.snapshot()
        assertEquals(42, WalletSelectionEpoch.runIfCurrentOrNull(epoch) { 42 })
        val failure = IllegalArgumentException("admitted fixture failure")
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
            WalletSelectionEpoch.runIfCurrentOrNull(epoch) { throw failure }
        })
        WalletSelectionEpoch.beginChange()
        try { assertNull(WalletSelectionEpoch.runIfCurrentOrNull(epoch) { fail("transition"); Unit }) }
        finally { WalletSelectionEpoch.endChange() }
        assertNull(WalletSelectionEpoch.runIfCurrentOrNull(epoch) { fail("stale"); Unit })
    }

    @Test fun `synchronous operation requires a stable current epoch and propagates its result`() {
        val current = WalletSelectionEpoch.snapshot()
        assertEquals(42, WalletSelectionEpoch.runIfCurrent(current) { 42 })
        WalletSelectionEpoch.beginChange()
        try { assertThrows(IllegalStateException::class.java) { WalletSelectionEpoch.runIfCurrent(current) { fail("transition") } } }
        finally { WalletSelectionEpoch.endChange() }
        assertThrows(IllegalStateException::class.java) { WalletSelectionEpoch.runIfCurrent(current) { fail("stale") } }
    }

    @Test fun `returning to same wallet cannot revive an earlier snapshot and nested mutations stay closed`() {
        val a = WalletSelectionEpoch.snapshot()
        WalletSelectionEpoch.beginChange()
        try {
            assertFalse(WalletSelectionEpoch.isCurrent(a))
            assertThrows(IllegalStateException::class.java) { WalletSelectionEpoch.snapshot() }
            WalletSelectionEpoch.beginChange()
            WalletSelectionEpoch.endChange()
            assertFalse(WalletSelectionEpoch.isCurrent(a))
        } finally { WalletSelectionEpoch.endChange() }
        val b = WalletSelectionEpoch.snapshot()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        assertFalse(WalletSelectionEpoch.isCurrent(a)); assertFalse(WalletSelectionEpoch.isCurrent(b))
        assertTrue(WalletSelectionEpoch.isCurrent(WalletSelectionEpoch.snapshot()))
    }
}
