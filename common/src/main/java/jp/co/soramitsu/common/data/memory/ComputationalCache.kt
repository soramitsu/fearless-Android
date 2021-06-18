package jp.co.soramitsu.common.data.memory

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class ComputationalCache {

    private val memory = mutableMapOf<String, Pair<MutableSet<Lifecycle>, Deferred<Any?>>>()

    /**
     * Caches computation till lifecycle is destroyed
     */
    @Suppress("UNCHECKED_CAST")
    @Synchronized
    suspend fun <T> useCache(
        key: String,
        lifecycle: Lifecycle,
        computation: suspend () -> T
    ): T = withContext(Dispatchers.Default) {
        val deferred = if (key in memory) {
            val (activeLifecycles, existingComputation) = memory[key]!!

            activeLifecycles += lifecycle

            existingComputation
        } else {
            val deferred = async(Dispatchers.Default) { computation() }

            memory[key] = mutableSetOf(lifecycle) to deferred

            deferred
        }

        withContext(Dispatchers.Main) {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    memory[key]?.let { (lifecycles, _) ->
                        lifecycles -= lifecycle

                        if (lifecycles.isEmpty()) {
                            memory.remove(key)
                        }
                    }

                    lifecycle.removeObserver(this)
                }
            })
        }

        deferred.await() as T
    }
}
