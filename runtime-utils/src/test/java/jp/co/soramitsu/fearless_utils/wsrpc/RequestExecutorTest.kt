package jp.co.soramitsu.fearless_utils.wsrpc

import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
}
