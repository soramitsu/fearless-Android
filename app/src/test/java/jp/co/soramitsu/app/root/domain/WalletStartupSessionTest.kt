package jp.co.soramitsu.app.root.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletStartupSessionTest {

    @Before
    fun resetSession() {
        WalletStartupSession.resetForTest()
    }

    @Test
    fun `heavy root stays blocked until current process gate succeeds`() {
        val check = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )

        assertFalse(WalletStartupSession.isReady())
        assertTrue(WalletStartupSession.isPending(check))

        assertTrue(WalletStartupSession.markReady(check))

        assertTrue(WalletStartupSession.isReady())
        assertFalse(WalletStartupSession.isPending(check))
    }

    @Test
    fun `stale success cannot authorize a newer pending check`() {
        val stale = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )
        val current = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )

        assertFalse(WalletStartupSession.markReady(stale))

        assertFalse(WalletStartupSession.isReady())
        assertFalse(WalletStartupSession.isPending(stale))
        assertTrue(WalletStartupSession.isPending(current))
    }

    @Test
    fun `stale failure cannot claim current error handling`() {
        val stale = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )
        val current = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )

        assertFalse(WalletStartupSession.isPending(stale))
        assertTrue(WalletStartupSession.isPending(current))
        assertFalse(WalletStartupSession.isReady())
    }

    @Test
    fun `current success authorizes exactly one handoff`() {
        val check = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )

        assertTrue(WalletStartupSession.markReady(check))
        assertFalse(WalletStartupSession.markReady(check))

        assertTrue(WalletStartupSession.isReady())
    }

    @Test
    fun `launcher entry cannot revoke a ready wallet process`() {
        val check = requireNotNull(
            WalletStartupSession.beginCheckUnlessReady()
        )
        assertTrue(WalletStartupSession.markReady(check))

        val unnecessaryCheck =
            WalletStartupSession.beginCheckUnlessReady()

        assertTrue(unnecessaryCheck == null)
        assertTrue(WalletStartupSession.isReady())
        assertFalse(WalletStartupSession.isPending(check))
    }

    @Test
    fun `launcher entry starts a check when process is not ready`() {
        val current = WalletStartupSession.beginCheckUnlessReady()

        assertTrue(current != null)
        assertFalse(WalletStartupSession.isReady())
        assertTrue(WalletStartupSession.isPending(requireNotNull(current)))
    }
}
