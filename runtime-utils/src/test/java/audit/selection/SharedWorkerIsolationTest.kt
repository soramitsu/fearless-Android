package audit.selection

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Delayed
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset

class SharedWorkerIsolationTest {
    private class ManualExecutor : AbstractExecutorService() {
        val tasks = mutableListOf<Runnable>()
        override fun execute(r: Runnable) { tasks += r }
        override fun shutdown() {}
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
        fun drain() { while (tasks.isNotEmpty())tasks.removeAt(0).run() }
    }
    private class Scheduled(val action: Runnable) : FutureTask<Unit>(action, Unit), ScheduledFuture<Unit> {
        override fun getDelay(unit: TimeUnit) = 0L
        override fun compareTo(other: Delayed) = 0
    }

    @Test fun resetOfOneRequestControllerDoesNotCancelSiblingOnSharedWorker() {
        val worker = ManualExecutor()

        val first = RequestExecutor(worker)

        val second = RequestExecutor(worker)
        var a = 0

        var b = 0

        first.execute { a++ }

        second.execute { b++ }

        first.reset()

        worker.drain()
        assertEquals(0, a)

        assertEquals(1, b)
        first.execute { a++ }

        second.execute { b++ }

        second.reset()

        worker.drain()
        assertEquals(1, a)

        assertEquals(1, b)
    }

    @Test fun resetOfOneReconnectControllerDoesNotCancelSiblingAndOldTaskCannotReplay() {
        val worker = mock(ScheduledExecutorService::class.java)

        val tasks = mutableListOf<Scheduled>()
        `when`(worker.schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))).thenAnswer {
            Scheduled(it.getArgument(0)).also { task -> tasks += task }
        }
        val first = Reconnector(executor = worker)

        val second = Reconnector(executor = worker)

        var a = 0

        var b = 0
        first.scheduleConnect(1) { a++ }

        second.scheduleConnect(1) { b++ }

        first.reset()
        assertTrue(tasks[0].isCancelled)

        assertFalse(tasks[1].isCancelled)
        tasks.forEach { it.run() }

        tasks.forEach { it.action.run() }
        assertEquals(0, a)

        assertEquals(1, b)
    }
}
