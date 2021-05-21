package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class SuspendableProperty<T> {
    private val value = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun invalidate() {
        value.resetReplayCache()
    }

    fun set(new: T) {
        value.tryEmit(new) // always successful, since BufferOverflow.DROP_OLDEST is used
    }

    suspend fun get(): T = value.first()

    fun observe(): Flow<T> = value
}

suspend inline fun <T, R> SuspendableProperty<T>.useValue(action: (T) -> R) : R = action(get())
