package jp.co.soramitsu.fearless_utils.wsrpc

import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Reached only when a test is already failing; the host can be very busy.
private const val WAIT_SECONDS = 30L

class RequestExecutorTest {
    @Test fun `reset interrupts running work and cancels every queued action`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            val executor = RequestExecutor(pool)
            val entered = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            val sends = AtomicInteger()
            executor.execute {
                entered.countDown()
                try { CountDownLatch(1).await() } catch (expected: InterruptedException) { interrupted.countDown() }
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            repeat(100) { executor.execute { sends.incrementAndGet() } }
            executor.reset()
            assertTrue(interrupted.await(3, TimeUnit.SECONDS))
            val drained = CountDownLatch(1)
            pool.execute { drained.countDown() }
            assertTrue(drained.await(3, TimeUnit.SECONDS))
            assertEquals(0, sends.get())
            // A subsequent connection can enqueue ordinary work normally.
            val next = CountDownLatch(1)
            executor.execute { next.countDown() }
            assertTrue(next.await(3, TimeUnit.SECONDS))
        } finally { pool.shutdownNow() }
    }

    @Test fun `reset interrupts the only running task`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            val executor = RequestExecutor(pool)
            val entered = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            executor.execute {
                entered.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (expected: InterruptedException) {
                    interrupted.countDown()
                }
            }
            assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
            executor.reset()
            assertTrue(interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * The fatal interleaving, forced on real threads. reset() holds its monitor and reads the size
     * of the set. The only task then finishes on the executor thread, and its done() removes it
     * without taking that monitor. Kotlin's toList() trusted the size and called iterator().next()
     * on the emptied set, which threw NoSuchElementException out of SocketService.disconnect().
     */
    @Test fun `reset survives the only task finishing while the snapshot is taken`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            val futures = CountReadHookMap()
            val executor = RequestExecutor(pool).apply { replaceFutures(futures) }
            val running = CountDownLatch(1)
            val finish = CountDownLatch(1)
            executor.execute {
                running.countDown()
                finish.await()
            }
            assertTrue(running.await(WAIT_SECONDS, TimeUnit.SECONDS))
            futures.onCountRead = {
                finish.countDown()
                futures.awaitEmpty()
            }

            executor.reset()

            assertNull("reset() read no count, so this test did not force the race", futures.onCountRead)
            val next = CountDownLatch(1)
            executor.execute { next.countDown() }
            assertTrue(next.await(WAIT_SECONDS, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    /** Runs a hook after a count is taken and before the caller gets it, so the count goes stale. */
    private class CountReadHookMap : ConcurrentHashMap<Future<*>, Boolean>() {
        @Volatile var onCountRead: (() -> Unit)? = null

        override val size: Int
            get() = super.size.also { runHook() }

        override fun mappingCount(): Long = super.mappingCount().also { runHook() }

        fun awaitEmpty() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
            while (!isEmpty() && System.nanoTime() < deadline) Thread.sleep(1)
            check(isEmpty()) { "The finished task did not remove itself" }
        }

        private fun runHook() {
            val hook = onCountRead ?: return
            onCountRead = null
            hook()
        }
    }

    // A view of the hooked map keeps the field's exact type, and needs no test seam in production.
    private fun RequestExecutor.replaceFutures(futures: CountReadHookMap) {
        val field = RequestExecutor::class.java.getDeclaredField("futures")
        field.isAccessible = true
        field.set(this, futures.keySet(true))
    }
}
