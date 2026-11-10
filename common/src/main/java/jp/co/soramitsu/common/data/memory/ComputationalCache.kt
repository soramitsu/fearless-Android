package jp.co.soramitsu.common.data.memory

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComputationalCache {

    private class Entry(val computation: Deferred<Any?>) {
        val owners = mutableMapOf<Lifecycle, DefaultLifecycleObserver>()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lifecycle callbacks and cache ownership are serialized on the main dispatcher.
    private val memory = mutableMapOf<String, Entry>()

    /** Caches successful computations while at least one requesting lifecycle remains alive. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> useCache(
        key: String,
        lifecycle: Lifecycle,
        computation: suspend () -> T
    ): T = withContext(Dispatchers.Main.immediate) {
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
            throw CancellationException("Cache owner is destroyed")
        }

        memory[key]?.takeIf { it.computation.isCancelled }?.let { removeEntry(key, it) }
        val entry = memory[key] ?: createEntry(key, computation)
        registerOwner(key, entry, lifecycle)
        entry.computation.await() as T
    }

    private fun <T> createEntry(key: String, computation: suspend () -> T): Entry {
        val entry = Entry(scope.async(start = CoroutineStart.LAZY) { computation() })
        memory[key] = entry
        entry.computation.invokeOnCompletion { error ->
            if (error != null) {
                scope.launch(Dispatchers.Main.immediate) { removeEntry(key, entry) }
            }
        }
        return entry
    }

    private fun registerOwner(
        key: String,
        entry: Entry,
        lifecycle: Lifecycle
    ) {
        if (lifecycle in entry.owners) return
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
                entry.owners.remove(lifecycle)
                if (entry.owners.isEmpty()) removeEntry(key, entry)
            }
        }
        entry.owners[lifecycle] = observer
        lifecycle.addObserver(observer)
    }

    private fun removeEntry(key: String, entry: Entry) {
        if (memory[key] !== entry) return
        memory.remove(key)
        entry.owners.forEach { (lifecycle, observer) -> lifecycle.removeObserver(observer) }
        entry.owners.clear()
        entry.computation.cancel()
    }
}
