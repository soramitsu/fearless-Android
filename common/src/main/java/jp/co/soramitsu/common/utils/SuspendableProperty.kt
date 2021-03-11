package jp.co.soramitsu.common.utils

import kotlinx.coroutines.channels.BufferOverflow
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
}