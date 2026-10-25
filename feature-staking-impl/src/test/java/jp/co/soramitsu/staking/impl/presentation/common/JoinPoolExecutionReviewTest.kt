package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class JoinPoolExecutionReviewTest {
    @Test fun `live claim authorizes exact intent and preserves operation result`() {
        val h = Harness()
        assertTrue(h.shared.joinFlowState.owns(h.claim))
        assertEquals(42, h.guard.runIfAuthorized("intent") { 42 })
        assertThrows(IllegalStateException::class.java) { h.guard.runIfAuthorized("other") { fail("wrong intent") } }
        assertTrue(h.review.isActive())
        assertTrue(h.review.revoke())
        assertFalse(h.review.revoke())
        assertFalse(h.review.isActive())
        h.assertRevoked()
    }

    @Test fun `every ownership invalidation rejects the physical primitive`() {
        val changes: List<Harness.() -> Unit> = listOf(
            { shared.mainState.set(shared.requireMainState.copy()) },
            { shared.joinFlowState.set(shared.requireJoinState.copy()) },
            { shared.joinFlowState.release(claim) },
            { shared.joinFlowState.consume(claim) },
            { review.revoke() },
            { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        )
        for (change in changes) {
            val h = Harness()
            h.change()
            h.assertRevoked()
        }
    }

    @Test fun `cancelled attempt and unfinished wallet transition cannot execute`() {
        val cancelled = Harness()
        cancelled.job.cancel()
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            cancelled.guard.runIfAuthorized("intent") { fail("cancelled attempt") }
        }
        val changing = Harness()
        WalletSelectionEpoch.beginChange()
        try { changing.assertRevoked() } finally { WalletSelectionEpoch.endChange() }
    }

    @Test fun `invalidation is ordered after an already admitted synchronous primitive`() {
        val changes: List<Harness.() -> Unit> = listOf(
            { shared.mainState.set(shared.requireMainState.copy()) },
            { shared.joinFlowState.set(shared.requireJoinState.copy()) },
            { review.revoke() },
            { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        )
        for (change in changes) {
            val h = Harness()
            val admitted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val failed = AtomicReference<Throwable?>()
            val executor = thread {
                try { h.guard.runIfAuthorized("intent") {
                    admitted.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                } } catch (error: Throwable) { failed.set(error) }
            }
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            val writer = thread { try { h.change() } catch (error: Throwable) { failed.set(error) } }
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (writer.state != Thread.State.BLOCKED && writer.isAlive && System.nanoTime() < deadline) Thread.yield()
                assertEquals("The competing invalidation must wait for the admitted primitive", Thread.State.BLOCKED, writer.state)
            } finally {
                release.countDown()
                executor.join(5_000)
                writer.join(5_000)
            }
            assertFalse(executor.isAlive)
            assertFalse(writer.isAlive)
            assertNull(failed.get())
            h.assertRevoked()
        }
    }

    @Test fun `submission admission executes once and cannot replay after output failure`() {
        val h = Harness()
        var writes = 0
        assertEquals(42, h.guard.runSubmissionIfAuthorized("intent") { writes++; 42 })
        assertThrows(IllegalStateException::class.java) { h.guard.runSubmissionIfAuthorized("intent") { writes++ } }
        assertEquals(1, writes)
        val failed = Harness()
        assertThrows(java.io.IOException::class.java) { failed.guard.runSubmissionIfAuthorized("intent") { throw java.io.IOException("held local output") } }
        assertThrows(IllegalStateException::class.java) { failed.guard.runSubmissionIfAuthorized("intent") { fail("ambiguous replay") } }
    }

    @Test fun `revocation before submission admission prevents the output primitive`() {
        val h = Harness()
        h.review.revoke()
        assertThrows(IllegalStateException::class.java) { h.guard.runSubmissionIfAuthorized("intent") { fail("revoked write") } }
    }

    @Test fun `admitted output does not hold context lifecycle or wallet selection locks`() {
        val changes: List<Harness.() -> Unit> = listOf(
            { shared.mainState.set(shared.requireMainState.copy()) },
            { shared.joinFlowState.set(shared.requireJoinState.copy()) },
            { review.revoke() },
            { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        )
        for (change in changes) {
            val h = Harness()
            val admitted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val invalidated = CountDownLatch(1)
            val failed = AtomicReference<Throwable?>()
            val executor = thread {
                try { h.guard.runSubmissionIfAuthorized("intent") {
                    admitted.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                } } catch (error: Throwable) { failed.set(error) }
            }
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            val writer = thread { try { h.change(); invalidated.countDown() } catch (error: Throwable) { failed.set(error) } }
            val responsive: Boolean
            try { responsive = invalidated.await(500, TimeUnit.MILLISECONDS) }
            finally { release.countDown(); executor.join(5_000); writer.join(5_000) }
            assertTrue("A stalled output must not delay invalidation", responsive)
            assertFalse(executor.isAlive)
            assertFalse(writer.isAlive)
            assertNull(failed.get())
            h.assertRevoked()
        }
    }

    private class Harness {
        val shared = StakingPoolSharedStateProvider().apply { mainState.set(StakingPoolState()) }
        val context = shared.mainState.capture()
        init { shared.joinFlowState.set(StakingPoolJoinFlowState(context = context)) }
        val claim = requireNotNull(shared.joinFlowState.tryClaim(shared.joinFlowState.capture()))
        val review = shared.joinExecutionReview(context)
        val job = Job()
        val guard = review.authorize(claim, "intent", job)
        fun assertRevoked() {
            assertThrows(IllegalStateException::class.java) { guard.runIfAuthorized("intent") { fail("revoked primitive") } }
        }
    }
}
