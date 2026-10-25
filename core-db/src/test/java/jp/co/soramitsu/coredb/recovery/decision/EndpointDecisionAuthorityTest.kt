package jp.co.soramitsu.coredb.recovery.decision

import org.junit.Assert.*
import org.junit.Test

class EndpointDecisionAuthorityTest {
    @Test fun revokeDeniesFinalCallbackAndAbandonedAttemptCannotReplay() {
        val authority = EndpointDecisionAuthority()
        val attempt = authority.newAttempt()
        authority.revoke()
        var entered = false
        try { authority.commitOnce(attempt) { entered = true }; fail() } catch (_: IllegalStateException) {}
        assertFalse(entered)
        assertFalse(authority.isCurrent())
        val fresh = EndpointDecisionAuthority()
        val abandoned = fresh.newAttempt()
        fresh.abandon(abandoned)
        try { fresh.commitOnce(abandoned) { entered = true }; fail() } catch (_: IllegalStateException) {}
        assertFalse(entered)
    }

    @Test fun foreignAuthorityAndFailedCallbackCannotGrantReplay() {
        val first = EndpointDecisionAuthority()
        val attempt = first.newAttempt()
        try { EndpointDecisionAuthority().commitOnce(attempt) {}; fail() } catch (_: IllegalArgumentException) {}
        val sentinel = IllegalStateException("synthetic failure")
        try { first.commitOnce(attempt) { throw sentinel }; fail() } catch (caught: IllegalStateException) {
            assertSame(sentinel, caught)
        }
        try { first.commitOnce(attempt) {}; fail() } catch (_: IllegalStateException) {}
        assertEquals("EndpointDecisionAuthority", first.toString())
    }
}
