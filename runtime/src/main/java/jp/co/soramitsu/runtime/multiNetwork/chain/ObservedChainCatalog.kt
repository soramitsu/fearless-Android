package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/** Shares the current catalog only among overlapping observers. */
internal class ObservedChainCatalog(
    private val source: () -> Flow<List<Chain>>,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var current: Session? = null

    private class Snapshot(val chains: List<Chain>)
    private class Event(val snapshot: Snapshot?, val complete: Boolean = false, val error: Throwable? = null)

    private inner class Session {
        var observers = 0
        // Catalogs are snapshots: slow observers need the latest state, including read failures.
        val values = MutableSharedFlow<Event>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        @Volatile private var latest: Snapshot? = null
        val producer = scope.launch(start = CoroutineStart.LAZY) {
            try {
                source().collect { chains ->
                    val snapshot = Snapshot(chains)
                    latest = snapshot
                    values.emit(Event(snapshot))
                }
                // Retain the final snapshot in the terminal event so a collector that attaches
                // after a finite source completes still receives its last value exactly once.
                values.emit(Event(latest, complete = true))
            } catch (error: Throwable) {
                values.tryEmit(Event(latest, complete = true, error = error))
                if (error is CancellationException) throw error
            }
        }.also { job ->
            // Also propagate cancellation before the lazy producer starts running.
            job.invokeOnCompletion { error ->
                if (error != null) values.tryEmit(Event(latest, complete = true, error = error))
            }
        }
    }

    fun observe(): Flow<List<Chain>> = flow {
        scope.coroutineContext.ensureActive()
        val session = synchronized(lock) {
            (current ?: Session().also { current = it }).also { it.observers++ }
        }
        try {
            session.producer.start()
            var observed: Snapshot? = null
            session.values.takeWhile { event ->
                // A failed read revokes the retained snapshot, even for a late first() collector.
                event.error?.let { throw it }
                event.snapshot?.let { snapshot ->
                    if (observed !== snapshot) {
                        emit(snapshot.chains)
                        observed = snapshot
                    }
                }
                !event.complete
            }.collect {}
        } finally {
            synchronized(lock) {
                session.observers--
                if (session.observers == 0) {
                    // Retire synchronously, before a new collector can acquire this replay.
                    check(current === session)
                    current = null
                    session.producer.cancel()
                }
            }
        }
    }
}
