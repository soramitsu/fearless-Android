package jp.co.soramitsu.coredb.recovery.decision

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointDecisionCommitCoordinatorTest {
    @Test fun `one current attempt commits exactly once and diagnostics contain no authority data`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        val calls = AtomicInteger()
        val receipt = Any()

        assertTrue(coordinator.isCurrent())
        assertEquals("EndpointDecisionCommitAttempt", attempt.toString())
        assertSame(receipt, coordinator.commitOnce(attempt) {
            calls.incrementAndGet()
            receipt
        })
        assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) { calls.incrementAndGet() }
        }
        assertEquals(1, calls.get())
    }

    @Test fun `revocation before final grant rejects without callback and consumes the attempt`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        var calls = 0
        coordinator.revoke()

        assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) { calls++ }
        }
        assertFalse(coordinator.isCurrent())
        assertEquals(0, calls)
        assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) { calls++ }
        }
        assertEquals(0, calls)
        assertThrows(IllegalStateException::class.java) { coordinator.newAttempt() }
    }

    @Test fun `revocation returns while an already granted physical commit remains blocked`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        val enteredCommit = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val commit = executor.submit<String> {
                coordinator.commitOnce(attempt) {
                    enteredCommit.countDown()
                    check(releaseCommit.await(5, TimeUnit.SECONDS))
                    "committed"
                }
            }
            assertTrue(enteredCommit.await(2, TimeUnit.SECONDS))
            val revoke = executor.submit { coordinator.revoke() }
            revoke.get(2, TimeUnit.SECONDS)

            assertFalse(coordinator.isCurrent())
            assertFalse(commit.isDone)
            assertThrows(IllegalStateException::class.java) { coordinator.newAttempt() }
            releaseCommit.countDown()
            assertEquals("committed", commit.get(2, TimeUnit.SECONDS))
            // Finishing a granted write does not restore authority to publish its result.
            assertFalse(coordinator.isCurrent())
        } finally {
            releaseCommit.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test fun `revoke is idempotent and a different coordinator cannot reuse an old attempt`() {
        val first = EndpointDecisionCommitCoordinator()
        val oldAttempt = first.newAttempt()
        first.revoke()
        first.revoke()
        val second = EndpointDecisionCommitCoordinator()
        var called = false

        assertFalse(first.isCurrent())
        assertTrue(second.isCurrent())
        assertThrows(IllegalArgumentException::class.java) {
            second.commitOnce(oldAttempt) { called = true }
        }
        assertThrows(IllegalArgumentException::class.java) { second.abandon(oldAttempt) }
        assertFalse(called)
        assertEquals("fresh", second.commitOnce(second.newAttempt()) { "fresh" })
    }

    @Test fun `abandoned SQL preparation cannot later receive a grant`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        val sqlFailure = IllegalStateException("Synthetic decision SQL failure")
        var commitCalls = 0

        val observed = assertThrows(IllegalStateException::class.java) {
            try {
                throw sqlFailure
            } finally {
                coordinator.abandon(attempt)
            }
        }

        assertSame(sqlFailure, observed)
        coordinator.abandon(attempt)
        assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) { commitCalls++ }
        }
        assertEquals(0, commitCalls)
        assertTrue(coordinator.isCurrent())
    }

    @Test fun `physical SQL failure propagates unchanged and consumes its grant`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        val sqlFailure = IllegalStateException("Synthetic physical commit failure")
        var calls = 0

        val observed = assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) {
                calls++
                throw sqlFailure
            }
        }

        assertSame(sqlFailure, observed)
        assertThrows(IllegalStateException::class.java) {
            coordinator.commitOnce(attempt) { calls++ }
        }
        assertEquals(1, calls)
        assertTrue(coordinator.isCurrent())
    }

    @Test fun `concurrent replay never enters the physical commit callback twice`() {
        val coordinator = EndpointDecisionCommitCoordinator()
        val attempt = coordinator.newAttempt()
        val enteredCommit = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val calls = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val commit = executor.submit {
                coordinator.commitOnce(attempt) {
                    calls.incrementAndGet()
                    enteredCommit.countDown()
                    check(releaseCommit.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(enteredCommit.await(2, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) {
                coordinator.commitOnce(attempt) { calls.incrementAndGet() }
            }
            assertEquals(1, calls.get())
            releaseCommit.countDown()
            commit.get(2, TimeUnit.SECONDS)
        } finally {
            releaseCommit.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
