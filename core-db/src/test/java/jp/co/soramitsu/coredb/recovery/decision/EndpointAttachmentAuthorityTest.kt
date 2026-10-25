package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EndpointAttachmentAuthorityTest {
    @Test fun exactCaptureGetsOneRequestAndSeparateCommitWithNoReusableGrant() = runBlocking {
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        var requests = 0; var commits = 0
        assertTrue(authority.requestOnce(attempt) { requests++; true })
        assertEquals("observed", authority.commitOnce(attempt, capture) { commits++; "observed" })
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { commits++ } }
        assertEquals(1, requests); assertEquals(1, commits)
        assertTrue(authority.isCurrent())
    }

    @Test fun structurallyEqualCaptureAndForeignAuthorityRejectBeforeCallback() = runBlocking {
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        assertTrue(authority.requestOnce(attempt) { true }); var commits = 0
        assertThrows(IllegalArgumentException::class.java) { authority.commitOnce(attempt, capture()) { commits++ } }
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentAuthority().commitOnce(attempt, capture) { commits++ } }
        assertEquals(0, commits)
        authority.commitOnce(attempt, capture) { commits++ }
        assertEquals(1, commits)
    }

    @Test fun revokeRetiresAuthorityAndAbandonConsumesOnlyExactAttempt() = runBlocking {
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val first = authority.newAttempt(capture)
        val second = authority.newAttempt(capture); authority.abandon(first)
        var requests = 0
        assertThrows(IllegalStateException::class.java) { runBlocking { authority.requestOnce(first) { requests++; true } } }
        assertTrue(authority.requestOnce(second) { requests++; true })
        authority.revoke(); assertFalse(authority.isCurrent())
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(second, capture) { error("unreachable") } }
        assertThrows(IllegalStateException::class.java) { authority.newAttempt(capture) }
        assertEquals(1, requests)
    }

    @Test fun observationRequiresExistingCaptureAndConsumesRequestAndCommitCapability() {
        val capture = capture(present = true); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        assertTrue(capture.alreadyPresent); authority.grantObservationOnce(attempt, capture)
        assertThrows(IllegalStateException::class.java) { runBlocking { authority.requestOnce(attempt) { error("unreachable") } } }
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { error("unreachable") } }
        val absent = capture(); val absentAttempt = authority.newAttempt(absent)
        assertFalse(absent.alreadyPresent)
        assertThrows(IllegalArgumentException::class.java) { authority.grantObservationOnce(absentAttempt, absent) }
        authority.abandon(absentAttempt)
    }

    @Test fun mismatchNeverCreatesCommitAuthorityAndFailingCallbackConsumesAttempt() = runBlocking<Unit> {
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        assertFalse(authority.requestOnce(attempt) { false })
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { error("unreachable") } }
        val failed = authority.newAttempt(capture); val sentinel = IllegalArgumentException("synthetic request failure")
        assertSame(sentinel, assertThrows(IllegalArgumentException::class.java) { runBlocking { authority.requestOnce(failed) { throw sentinel } } })
        assertThrows(IllegalStateException::class.java) { authority.commitOnce(failed, capture) { error("unreachable") } }
    }

    @Test fun resultInvariantsPreventActiveFreshWriteClaimsAndDiagnosticsOmitPayload() {
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentResult(EndpointAttachmentResult.Kind.ADDED_INACTIVE, 1, 1, true, true, false) }
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentResult(EndpointAttachmentResult.Kind.ADDED_INACTIVE, 1, 1, false, false, false) }
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentResult(EndpointAttachmentResult.Kind.REATTACHED_INACTIVE, -1, 2, false, true, false) }
        val result = EndpointAttachmentResult(EndpointAttachmentResult.Kind.ALREADY_PRESENT, -1, Long.MAX_VALUE, true, false, false)
        assertTrue(result.isActive); assertFalse(result.matchedThisInvocation)
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        for (text in listOf(capture.toString(), attempt.toString(), result.toString())) {
            assertFalse(text.contains("payload-name")); assertFalse(text.contains("wss://")); assertFalse(text.contains("private-path"))
        }
    }

    @Test fun preliminaryAdmissionRequiresExactCaptureAndAuthorityWithoutConsumingFinalGrant() {
        val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
        assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
        assertThrows(IllegalArgumentException::class.java) { authority.requireMatched(attempt, capture()) }
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentAuthority().requireMatched(attempt, capture) }
        repeat(2) { authority.requireMatched(attempt, capture) }
        var commits = 0
        authority.commitOnce(attempt, capture) { commits++ }
        assertEquals(1, commits)
        assertThrows(IllegalStateException::class.java) { authority.requireMatched(attempt, capture) }
    }

    @Test fun preliminaryAdmissionRejectsNoRequestMismatchRevocationAndAbandonment() {
        for (mode in listOf("NEW", "FALSE", "REVOKED", "ABANDONED")) {
            val capture = capture(); val authority = EndpointAttachmentAuthority(); val attempt = authority.newAttempt(capture)
            when (mode) {
                "FALSE" -> assertFalse(runBlocking { authority.requestOnce(attempt) { false } })
                "REVOKED", "ABANDONED" -> {
                    assertTrue(runBlocking { authority.requestOnce(attempt) { true } })
                    if (mode == "REVOKED") authority.revoke() else authority.abandon(attempt)
                }
            }
            var commits = 0
            assertThrows(IllegalStateException::class.java) { authority.requireMatched(attempt, capture) }
            assertThrows(IllegalStateException::class.java) { authority.commitOnce(attempt, capture) { commits++ } }
            assertEquals(0, commits)
        }
    }

    private fun capture(present: Boolean = false): EndpointAttachmentCapture {
        val source = EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.LEGACY, -1), "a".repeat(64))
        val candidate = EndpointDecisionCandidate("payload-name", "wss://example.invalid/private-path")
        val target = EndpointDecisionTarget("1".repeat(64), "b".repeat(64), EndpointDecisionTarget.Family.SUBSTRATE, "0x" + "1".repeat(64))
        val decision = EndpointRecoveryDecision(1, 1, source, candidate, target)
        val owner = if (present) EndpointAttachmentPolicy.Owner(-1, 1, 1, 0, true) else null
        val projection = if (present) EndpointAttachmentPolicy.Projection(0, 0, true) else null
        return EndpointAttachmentCapture(decision, EndpointAttachmentAction.ADD_NEW, owner, null, projection,
            EndpointAttachmentPolicy.plan(EndpointAttachmentPolicy.Action.ADD_NEW, owner, projection, null, null))
    }
}
