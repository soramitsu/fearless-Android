package jp.co.soramitsu.fearless_utils.wsrpc.recovery

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val DEFAULT_RECONNECT_STRATEGY =
    ExponentialReconnectStrategy(initialTime = 300L, base = 2.0)

class Reconnector(
    private val strategy: ReconnectStrategy = DEFAULT_RECONNECT_STRATEGY,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) {
    private class Attempt { var started = false }
    private val lifecycle = Any()
    private var identity: Attempt? = null
    private var inProgress: Future<*>? = null

    fun scheduleConnect(currentAttempt: Int, runnable: Runnable) {
        val token = Attempt()
        val previous = synchronized(lifecycle) {
            identity = token
            inProgress.also { inProgress = null }
        }
        previous?.cancel(true)
        val future = try {
            executor.schedule({
                val admitted = synchronized(lifecycle) {
                    if (identity !== token || token.started) false else {
                        token.started = true
                        inProgress = null
                        true
                    }
                }
                // Never hold the scheduler lock across a caller's state lock. SocketService also
                // checks its captured reconnect identity after acquiring its own monitor.
                if (admitted) runnable.run()
            }, strategy.getTimeForReconnect(currentAttempt), TimeUnit.MILLISECONDS)
        } catch (failure: RuntimeException) {
            synchronized(lifecycle) { if (identity === token) identity = null }
            throw failure
        }
        val obsolete = synchronized(lifecycle) {
            if (identity !== token) true else {
                if (!token.started) inProgress = future
                false
            }
        }
        if (obsolete) future.cancel(true)
    }

    fun reset() {
        val previous = synchronized(lifecycle) {
            identity = null
            inProgress.also { inProgress = null }
        }
        previous?.cancel(true)
    }
}
