package jp.co.soramitsu.staking.impl.domain.validators.contract

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchContractEnvironment : ExternalResource() {
    private lateinit var locale: Locale
    private lateinit var logging: MockedStatic<Log>
    override fun before() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        logging = mockStatic(Log::class.java)
    }
    override fun after() {
        logging.close()
        Dispatchers.resetMain()
        Locale.setDefault(locale)
    }
}

internal class SearchContractLifecycle : Lifecycle(), LifecycleOwner {
    private val observers = linkedSetOf<LifecycleObserver>()
    private var state = State.STARTED
    override val lifecycle: Lifecycle get() = this
    override val currentState: State get() = state
    override fun addObserver(observer: LifecycleObserver) { observers.add(observer) }
    override fun removeObserver(observer: LifecycleObserver) { observers.remove(observer) }
    fun destroy() {
        state = State.DESTROYED
        observers.toList().forEach { (it as DefaultLifecycleObserver).onDestroy(this) }
    }
}
