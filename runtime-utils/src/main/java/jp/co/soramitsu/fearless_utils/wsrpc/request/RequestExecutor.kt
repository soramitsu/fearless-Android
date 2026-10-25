package jp.co.soramitsu.fearless_utils.wsrpc.request

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

typealias SendAction = () -> Unit

class RequestExecutor(private val executor: ExecutorService = Executors.newSingleThreadExecutor()) {
    private val futures = ConcurrentHashMap.newKeySet<Future<*>>()
    private val lifecycle = Any()
    private var generation = 0L

    fun execute(action: SendAction) {
        val future = synchronized(lifecycle) {
            val requestGeneration = generation
            object : FutureTask<Unit>(Callable {
                if (synchronized(lifecycle) { generation == requestGeneration }) action()
            }) {
                override fun done() { futures.remove(this) }
            }.also { futures += it }
        }
        // Register before execution: an immediate executor can finish synchronously.
        try {
            executor.execute(future)
        } catch (failure: RuntimeException) {
            future.cancel(false)
            throw failure
        }
    }

    fun reset() {
        // Cancellation invokes done(), which removes from the concurrent set.
        // No collection monitor is held while interrupting a running action.
        val pending = synchronized(lifecycle) {
            generation++
            futures.toList()
        }
        pending.forEach { it.cancel(true) }
    }
}
