package jp.co.soramitsu.app.root.data.runtime

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class RuntimeHolder {
    private val runtimeFlow = MutableSharedFlow<RuntimeSnapshot>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun get(): RuntimeSnapshot = runtimeFlow.first()

    fun set(runtimeSnapshot: RuntimeSnapshot) {
        runtimeFlow.tryEmit(runtimeSnapshot)
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    fun invalidate() {
        runtimeFlow.resetReplayCache()
    }
}