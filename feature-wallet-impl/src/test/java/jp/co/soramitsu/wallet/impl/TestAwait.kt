package jp.co.soramitsu.wallet.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.shadows.ShadowLooper

/** Observes [this] until it holds a value matching [predicate]; work on real dispatchers gets up to [timeoutMs]. */
internal fun <T> LiveData<T>.awaitValue(timeoutMs: Long = 5_000, predicate: (T?) -> Boolean = { it != null }): T? {
    val observer = Observer<T> { }
    observeForever(observer)
    try {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate(value) && System.currentTimeMillis() < deadline) {
            runCatching { ShadowLooper.idleMainLooper() }
            Thread.sleep(10)
        }
        return value
    } finally {
        removeObserver(observer)
    }
}

/** The first value of [this] matching [predicate], waiting up to [timeoutMs] of real time. */
internal fun <T> Flow<T>.awaitFirst(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T = runBlocking {
    withTimeout(timeoutMs) { first(predicate) }
}
