package jp.co.soramitsu.success

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.flags.FragmentGetContextFix
import dagger.hilt.android.internal.builders.FragmentComponentBuilder
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories
import dagger.hilt.android.internal.lifecycle.HiltViewModelFactory
import dagger.hilt.android.internal.managers.FragmentComponentManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import jp.co.soramitsu.feature_success_impl.R
import jp.co.soramitsu.success.presentation.SuccessFragment_GeneratedInjector
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Test-only stand-in for the Hilt components the `@AndroidEntryPoint` fragments of this module ask for.
 *
 * The fragments keep their generated Hilt base classes; only the components are replaced, so each test decides how a
 * `@HiltViewModel` is built ([viewModel]) and the screen runs against mocked collaborators. The success sheet has no injected
 * fields, so injection itself does nothing.
 */
object FragmentHiltShim {
    private val loader = FragmentHiltShim::class.java.classLoader
    private val viewModels = ConcurrentHashMap<Class<*>, (SavedStateHandle) -> ViewModel>()
    private val fragmentInjectors = arrayOf<Class<*>>(SuccessFragment_GeneratedInjector::class.java)

    fun <T : ViewModel> viewModel(type: KClass<T>, create: (SavedStateHandle) -> T) {
        viewModels[type.java] = create
    }

    private val hosts = mutableListOf<ActivityController<ShimHostActivity>>()

    /** Starts a host activity; [reset] destroys it, so no composition outlives its test. */
    fun launchHost(): ShimHostActivity = Robolectric.buildActivity(ShimHostActivity::class.java).setup().also { hosts += it }.get()

    /**
     * Destroys the hosts and drains the main looper. Robolectric drops queued main-looper work between tests, and a
     * dropped Compose UI dispatch would leave every later Compose test of this JVM waiting for idleness.
     */
    fun reset() {
        viewModels.clear()
        hosts.forEach { controller -> runCatching { controller.pause().stop().destroy() } }
        hosts.clear()
        repeat(DRAIN_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(DRAIN_PAUSE_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private const val DRAIN_ROUNDS = 5
    private const val DRAIN_PAUSE_MS = 20L

    /** The application component: Hilt fragments only read the getContext() fix flag from it. */
    val applicationComponent: Any by lazy {
        component(FragmentGetContextFix.FragmentGetContextFixEntryPoint::class.java) { name, _ ->
            if (name == "getDisableFragmentGetContextFix") emptySet<Boolean>() else unsupported(name)
        }
    }

    fun activityComponent(): Any = component(FragmentComponentManager.FragmentComponentBuilderEntryPoint::class.java) { name, _ ->
        if (name == "fragmentComponentBuilder") builder(FragmentComponentBuilder::class.java) { fragmentComponent() } else unsupported(name)
    }

    private fun fragmentComponent(): Any = component(
        FragmentComponent::class.java,
        DefaultViewModelFactories.FragmentEntryPoint::class.java,
        *fragmentInjectors
    ) { name, _ ->
        when {
            name.startsWith("inject") -> null
            name == "getHiltInternalFactoryFactory" -> factoryFactory()
            else -> unsupported(name)
        }
    }

    private fun factoryFactory(): DefaultViewModelFactories.InternalFactoryFactory {
        val constructor = DefaultViewModelFactories.InternalFactoryFactory::class.java
            .getDeclaredConstructor(Map::class.java, ViewModelComponentBuilder::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(viewModels.keys.associateWith { true }, viewModelComponentBuilder())
    }

    private fun viewModelComponentBuilder(): ViewModelComponentBuilder {
        var handle = SavedStateHandle()
        return Proxy.newProxyInstance(loader, arrayOf<Class<*>>(ViewModelComponentBuilder::class.java)) { proxy, method, args ->
            when (method.name) {
                "savedStateHandle" -> proxy.also { handle = args!![0] as SavedStateHandle }
                "viewModelLifecycle" -> proxy
                "build" -> viewModelComponent(handle)
                else -> objectMethod(proxy, method, args)
            }
        } as ViewModelComponentBuilder
    }

    private fun viewModelComponent(handle: SavedStateHandle): Any = component(
        ViewModelComponent::class.java,
        HiltViewModelFactory.ViewModelFactoriesEntryPoint::class.java
    ) { name, _ ->
        when (name) {
            "getHiltViewModelMap" -> viewModels.mapValues { (_, create) -> Provider<ViewModel> { create(handle) } }
            "getHiltViewModelAssistedMap" -> emptyMap<Class<*>, Any>()
            else -> unsupported(name)
        }
    }

    private fun builder(type: Class<*>, build: () -> Any): Any =
        Proxy.newProxyInstance(loader, arrayOf<Class<*>>(type)) { proxy, method, args ->
            when (method.name) {
                "build" -> build()
                "toString", "hashCode", "equals" -> objectMethod(proxy, method, args)
                else -> proxy
            }
        }

    private fun component(vararg interfaces: Class<*>, handler: (String, Array<out Any?>) -> Any?): Any =
        Proxy.newProxyInstance(loader, arrayOf<Class<*>>(GeneratedComponent::class.java, *interfaces)) { proxy, method, args ->
            when (method.name) {
                "toString", "hashCode", "equals" -> objectMethod(proxy, method, args)
                else -> handler(method.name, args ?: emptyArray())
            }
        }

    private fun objectMethod(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
        "toString" -> "FragmentHiltShim(${proxy.javaClass.interfaces.joinToString { it.simpleName }})"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.get(0)
        else -> unsupported(method.name)
    }

    private fun unsupported(name: String): Nothing = throw UnsupportedOperationException("FragmentHiltShim does not provide $name")
}

/** An application whose singleton component is [FragmentHiltShim]'s, as a `@HiltAndroidApp` application's would be. */
class ShimApplication : Application(), GeneratedComponentManager<Any> {
    override fun generatedComponent(): Any = FragmentHiltShim.applicationComponent
}

/** A host activity whose component is [FragmentHiltShim], as an `@AndroidEntryPoint` activity's would be. */
class ShimHostActivity : FragmentActivity(), GeneratedComponentManager<Any> {
    private val component by lazy { FragmentHiltShim.activityComponent() }

    override fun generatedComponent(): Any = component

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sheets and dialogs shown by the fragments are Material components.
        setTheme(R.style.Theme_Soramitsu_Fearless)
        super.onCreate(savedInstanceState)
    }
}

/** Runs the main looper until [condition] holds, giving background dispatchers real time to finish. */
fun idleMainUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.nanoTime() < deadline) { "Condition not met within $timeoutMillis ms" }
        Thread.sleep(5)
    }
}

fun launchHost(): ShimHostActivity = FragmentHiltShim.launchHost()
