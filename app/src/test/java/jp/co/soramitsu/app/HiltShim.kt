package jp.co.soramitsu.app

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.internal.builders.ActivityComponentBuilder
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder
import dagger.hilt.android.internal.builders.FragmentComponentBuilder
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories
import dagger.hilt.android.internal.lifecycle.HiltViewModelFactory
import dagger.hilt.android.internal.lifecycle.RetainedLifecycleImpl
import dagger.hilt.android.internal.managers.ActivityComponentManager
import dagger.hilt.android.internal.managers.FragmentComponentManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Provider
import jp.co.soramitsu.app.root.presentation.AlertFragment_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.RootActivity_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.WalletRootActivity_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.emergency.WalletEmergencyExportActivity_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.keyloss.WalletKeyLossRecoveryActivity_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.main.MainFragment_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.main.hub.MainHubFragment_GeneratedInjector
import jp.co.soramitsu.app.root.presentation.stories.StoryFragment_GeneratedInjector
import kotlin.reflect.KClass
import org.robolectric.Shadows.shadowOf

/**
 * Test-only stand-in for the generated Hilt graph.
 *
 * The production `@AndroidEntryPoint` classes keep their generated base classes; only the components they ask for
 * are replaced. Each test registers how a class is injected ([inject]) and how a `@HiltViewModel` is built
 * ([viewModel]), so screens run with mocked collaborators instead of the wallet's real singletons.
 */
object HiltShim {
    private val loader = HiltShim::class.java.classLoader
    private val injectors = ConcurrentHashMap<Class<*>, (Any) -> Unit>()
    private val viewModels = ConcurrentHashMap<Class<*>, (SavedStateHandle) -> ViewModel>()

    private val activityInjectors = arrayOf<Class<*>>(
        WalletStartupActivity_GeneratedInjector::class.java,
        RootActivity_GeneratedInjector::class.java,
        WalletRootActivity_GeneratedInjector::class.java,
        WalletEmergencyExportActivity_GeneratedInjector::class.java,
        WalletKeyLossRecoveryActivity_GeneratedInjector::class.java
    )
    private val fragmentInjectors = arrayOf<Class<*>>(
        MainFragment_GeneratedInjector::class.java,
        MainHubFragment_GeneratedInjector::class.java,
        StoryFragment_GeneratedInjector::class.java,
        AlertFragment_GeneratedInjector::class.java
    )

    fun <T : Any> inject(type: KClass<T>, block: (T) -> Unit) {
        injectors[type.java] = { target -> block(type.java.cast(target)) }
    }

    fun <T : ViewModel> viewModel(type: KClass<T>, create: (SavedStateHandle) -> T) {
        viewModels[type.java] = create
    }

    fun reset() {
        injectors.clear()
        viewModels.clear()
    }

    val singletonComponent: Any by lazy {
        component(managerType("ActivityRetainedComponentBuilderEntryPoint")) { name, _ ->
            if (name == "retainedComponentBuilder") builder(ActivityRetainedComponentBuilder::class.java) { retainedComponent() }
            else unsupported(name)
        }
    }

    fun activityComponent(): Any = component(
        ActivityComponent::class.java,
        DefaultViewModelFactories.ActivityEntryPoint::class.java,
        FragmentComponentManager.FragmentComponentBuilderEntryPoint::class.java,
        *activityInjectors
    ) { name, args ->
        when {
            name.startsWith("inject") -> injectInto(checkNotNull(args[0]))
            name == "getHiltInternalFactoryFactory" -> factoryFactory()
            name == "fragmentComponentBuilder" -> builder(FragmentComponentBuilder::class.java) { fragmentComponent() }
            else -> unsupported(name)
        }
    }

    private fun retainedComponent(): Any {
        val lifecycle = RetainedLifecycleImpl()
        return component(
            ActivityRetainedComponent::class.java,
            ActivityComponentManager.ActivityComponentBuilderEntryPoint::class.java,
            managerType("ActivityRetainedLifecycleEntryPoint")
        ) { name, _ ->
            when (name) {
                "activityComponentBuilder" -> builder(ActivityComponentBuilder::class.java) { activityComponent() }
                "getActivityRetainedLifecycle" -> lifecycle
                else -> unsupported(name)
            }
        }
    }

    private fun fragmentComponent(): Any = component(
        FragmentComponent::class.java,
        DefaultViewModelFactories.FragmentEntryPoint::class.java,
        *fragmentInjectors
    ) { name, args ->
        when {
            name.startsWith("inject") -> injectInto(checkNotNull(args[0]))
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
        return Proxy.newProxyInstance(loader, arrayOf(ViewModelComponentBuilder::class.java)) { proxy, method, args ->
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

    private fun injectInto(target: Any): Any? {
        val injector = injectors[target.javaClass]
            ?: error("No HiltShim injector registered for ${target.javaClass.name}")
        injector(target)
        return null
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
        "toString" -> "HiltShim(${proxy.javaClass.interfaces.joinToString { it.simpleName }})"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.get(0)
        else -> unsupported(method.name)
    }

    // ActivityRetainedComponentManager is package-private; its public entry points are reached by name.
    private fun managerType(entryPoint: String): Class<*> =
        Class.forName("dagger.hilt.android.internal.managers.ActivityRetainedComponentManager\$$entryPoint", false, loader)

    private fun unsupported(name: String): Nothing = throw UnsupportedOperationException("HiltShim does not provide $name")
}

/** Application whose singleton component is [HiltShim]: Hilt activities and fragments resolve their shim components. */
class HiltShimApplication : Application(), GeneratedComponentManager<Any> {
    override fun generatedComponent(): Any = HiltShim.singletonComponent
}

/** A Hilt-compatible host for `@AndroidEntryPoint` fragments under test. */
class HiltShimHostActivity : AppCompatActivity(), GeneratedComponentManager<Any> {
    private val component by lazy { HiltShim.activityComponent() }

    override fun generatedComponent(): Any = component

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Soramitsu_Fearless)
        super.onCreate(savedInstanceState)
    }
}

/** Runs the main looper until [condition] holds; background work (Dispatchers.IO) gets real time to finish. */
fun idleMainUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.nanoTime() < deadline) { "Condition not met within $timeoutMillis ms" }
        Thread.sleep(5)
    }
}
