package jp.co.soramitsu.coredb.recovery.decision

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EndpointAttachmentCoordinatorTest {
    @Test fun exactAttemptGetsOneRequestAndOneSeparateCommitGrant() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        var requests = 0
        assertTrue(gate.requestOnce(attempt) { requests++; true })
        assertEquals("committed", gate.commitOnce(attempt) { "committed" })
        assertEquals(1, requests)
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("replayed") } }
        assertThrows(IllegalStateException::class.java) { runBlocking { gate.requestOnce(attempt) { fail("replayed"); true } } }
    }

    @Test fun revokeBeforeRequestInvokesNoCallbackAndAttemptCannotRetry() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        gate.revoke()
        assertFalse(gate.isCurrent())
        assertThrows(IllegalStateException::class.java) { gate.newAttempt() }
        repeat(2) { assertThrows(IllegalStateException::class.java) { runBlocking { gate.requestOnce(attempt) { fail("request"); true } } } }
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit") } }
    }

    @Test fun mismatchFailureCancellationAndAbandonNeverReachCommitCallback() {
        for (mode in 0..3) {
            val gate = EndpointAttachmentCoordinator()
            val attempt = gate.newAttempt()
            when (mode) {
                0 -> runBlocking { assertFalse(gate.requestOnce(attempt) { false }) }
                1 -> assertThrows(IllegalArgumentException::class.java) { runBlocking { gate.requestOnce(attempt) { throw IllegalArgumentException("synthetic") } } }
                2 -> assertThrows(CancellationException::class.java) { runBlocking { gate.requestOnce(attempt) { throw CancellationException("synthetic") } } }
                3 -> gate.abandon(attempt)
            }
            assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit") } }
            assertThrows(IllegalStateException::class.java) { runBlocking { gate.requestOnce(attempt) { fail("request"); true } } }
        }
    }

    @Test fun commitWithoutMatchingRequestAndCrossAuthorityAttemptReject() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val other = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { other.requestOnce(attempt) { fail("request"); true } } }
        assertThrows(IllegalArgumentException::class.java) { other.abandon(attempt) }
        assertThrows(IllegalArgumentException::class.java) { other.commitOnce(attempt) { fail("commit") } }
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("no match") } }
        assertTrue(gate.requestOnce(attempt) { true })
        assertEquals(1, gate.commitOnce(attempt) { 1 })
    }

    @Test fun revocationAfterMatchedRequestRejectsFinalGrantWithoutClaimingSqlRollback() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        gate.revoke()
        repeat(2) { assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit callback") } } }
    }

    @Test fun revokeDoesNotWaitForAlreadyGrantedRequestAndPreventsItsLaterCommit() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoked = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { runBlocking { gate.requestOnce(attempt) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); true } } }
            catch (caught: Throwable) { failure.set(caught) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread { gate.revoke(); revoked.countDown() }.start()
            assertTrue("revocation must finish while request remains blocked", revoked.await(1, TimeUnit.SECONDS))
        } finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is IllegalStateException)
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit callback") } }
    }

    @Test fun revokeAfterFinalGrantDoesNotWaitAndAlreadyAuthorizedCallbackMayFinish() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoked = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { result.set(gate.commitOnce(attempt) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "completed" }) }
            catch (caught: Throwable) { failure.set(caught) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread { gate.revoke(); revoked.countDown() }.start()
            assertTrue("revocation must not wait for storage callback", revoked.await(1, TimeUnit.SECONDS))
            assertFalse(gate.isCurrent())
        } finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive)
        assertNull(failure.get())
        assertEquals("completed", result.get())
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("replayed") } }
    }

    @Test fun failingPhysicalCommitConsumesGrantAndPropagatesExactFailure() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        val sentinel = IllegalStateException("synthetic storage failure")
        val observed = assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { throw sentinel } }
        assertSame(sentinel, observed)
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("retry") } }
    }

    @Test fun abandonWinsWhileRequestIsWaitingForItsJointGrant() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { runBlocking { gate.requestOnce(attempt) { fail("abandoned request"); true } } }
            catch (caught: Throwable) { failure.set(caught) }
        }
        synchronized(gate) {
            worker.start()
            awaitGrantMonitor(worker)
            gate.abandon(attempt)
        }
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is IllegalStateException)
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit") } }
    }

    @Test fun abandonWinsWhileCommitIsWaitingForItsJointGrant() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { gate.commitOnce(attempt) { fail("abandoned commit") } }
            catch (caught: Throwable) { failure.set(caught) }
        }
        synchronized(gate) {
            worker.start()
            awaitGrantMonitor(worker)
            gate.abandon(attempt)
        }
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is IllegalStateException)
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("replay") } }
    }

    @Test fun abandonAfterRequestGrantDoesNotWaitAndDiscardsItsMatch() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val abandoned = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { runBlocking { gate.requestOnce(attempt) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); true } } }
            catch (caught: Throwable) { failure.set(caught) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread { gate.abandon(attempt); abandoned.countDown() }.start()
            assertTrue("abandon must finish while request remains blocked", abandoned.await(1, TimeUnit.SECONDS))
        } finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is IllegalStateException)
        assertTrue(gate.isCurrent())
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit") } }
    }

    @Test fun abandonAfterCommitGrantDoesNotWaitAndGrantedCommitMayFinish() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val abandoned = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { result.set(gate.commitOnce(attempt) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "completed" }) }
            catch (caught: Throwable) { failure.set(caught) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread { gate.abandon(attempt); abandoned.countDown() }.start()
            assertTrue("abandon must not wait for storage callback", abandoned.await(1, TimeUnit.SECONDS))
        } finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive)
        assertNull(failure.get())
        assertEquals("completed", result.get())
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("replayed") } }
    }

    private fun awaitGrantMonitor(worker: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (worker.isAlive && worker.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        assertEquals("grant must share revocation/abandonment state monitor", Thread.State.BLOCKED, worker.state)
    }

    @Test fun observationGrantIsConsumedWithoutRequestOrCommitAuthority() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        gate.grantObservationOnce(attempt)
        assertTrue(gate.isCurrent())
        assertThrows(IllegalStateException::class.java) { gate.grantObservationOnce(attempt) }
        assertThrows(IllegalStateException::class.java) { runBlocking { gate.requestOnce(attempt) { fail("request"); true } } }
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { fail("commit") } }
    }

    @Test fun revokedAbandonedAndForeignObservationAttemptsReject() {
        repeat(2) { kind ->
            val gate = EndpointAttachmentCoordinator()
            val attempt = gate.newAttempt()
            if (kind == 0) gate.revoke() else gate.abandon(attempt)
            assertThrows(IllegalStateException::class.java) { gate.grantObservationOnce(attempt) }
        }
        val gate = EndpointAttachmentCoordinator()
        assertThrows(IllegalArgumentException::class.java) {
            gate.grantObservationOnce(EndpointAttachmentCoordinator().newAttempt())
        }
    }

    @Test fun matchedRequestCannotBeReclassifiedAsUnprobedObservation() = runBlocking<Unit> {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        assertTrue(gate.requestOnce(attempt) { true })
        assertThrows(IllegalStateException::class.java) { gate.grantObservationOnce(attempt) }
        assertEquals(1, gate.commitOnce(attempt) { 1 })
    }

    @Test fun abandonWinsWhileObservationWaitsForItsJointGrant() {
        val gate = EndpointAttachmentCoordinator()
        val attempt = gate.newAttempt()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { gate.grantObservationOnce(attempt) }
            catch (caught: Throwable) { failure.set(caught) }
        }
        synchronized(gate) {
            worker.start()
            awaitGrantMonitor(worker)
            gate.abandon(attempt)
        }
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is IllegalStateException)
    }
    @Test fun preliminaryMatchAdmissionRejectsUnmatchedRevokedAbandonedAndForeignAttemptsWithoutCallback() {
        for (state in listOf("NEW", "FALSE", "REVOKED", "ABANDONED", "CONSUMED")) {
            val gate = EndpointAttachmentCoordinator(); val attempt = gate.newAttempt()
            var callbacks = 0
            when (state) {
                "FALSE" -> assertFalse(runBlocking { gate.requestOnce(attempt) { false } })
                "REVOKED", "ABANDONED", "CONSUMED" -> {
                    assertTrue(runBlocking { gate.requestOnce(attempt) { true } })
                    when (state) { "REVOKED" -> gate.revoke(); "ABANDONED" -> gate.abandon(attempt)
                        else -> gate.commitOnce(attempt) { Unit } }
                }
            }
            assertThrows(IllegalStateException::class.java) { gate.requireMatched(attempt) }
            assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { callbacks++ } }
            assertEquals(0, callbacks)
        }
        val gate = EndpointAttachmentCoordinator(); val attempt = gate.newAttempt()
        assertTrue(runBlocking { gate.requestOnce(attempt) { true } })
        assertThrows(IllegalArgumentException::class.java) { EndpointAttachmentCoordinator().requireMatched(attempt) }
        gate.requireMatched(attempt)
    }

    @Test fun preliminaryMatchAdmissionDoesNotConsumeGrantOrBypassLaterRevocation() {
        val gate = EndpointAttachmentCoordinator(); val attempt = gate.newAttempt()
        assertTrue(runBlocking { gate.requestOnce(attempt) { true } })
        repeat(2) { gate.requireMatched(attempt) }
        var callbacks = 0
        gate.revoke()
        assertThrows(IllegalStateException::class.java) { gate.commitOnce(attempt) { callbacks++ } }
        assertEquals(0, callbacks)
        val current = EndpointAttachmentCoordinator(); val live = current.newAttempt()
        assertTrue(runBlocking { current.requestOnce(live) { true } })
        repeat(2) { current.requireMatched(live) }
        current.commitOnce(live) { callbacks++ }
        assertEquals(1, callbacks)
        assertThrows(IllegalStateException::class.java) { current.requireMatched(live) }
    }

}
