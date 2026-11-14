package jp.co.soramitsu.common.data.memory

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource

@OptIn(ExperimentalCoroutinesApi::class)
internal class CacheMainRule : ExternalResource() {
    override fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    override fun after() { Dispatchers.resetMain() }
}

internal class CacheLifecycle : Lifecycle(), LifecycleOwner {
    private val observers = linkedSetOf<LifecycleObserver>()
    private var state = State.STARTED
    val observerCount = MutableStateFlow(0)
    override val lifecycle: Lifecycle get() = this
    override val currentState: State get() = state

    override fun addObserver(observer: LifecycleObserver) {
        observers += observer
        observerCount.value = observers.size
    }

    override fun removeObserver(observer: LifecycleObserver) {
        observers -= observer
        observerCount.value = observers.size
    }

    suspend fun awaitOwner() = withContext(Dispatchers.Default) {
        withTimeout(3_000) { observerCount.first { it > 0 } }
    }

    fun destroy() {
        state = State.DESTROYED
        observers.toList().forEach { (it as DefaultLifecycleObserver).onDestroy(this) }
    }
}
