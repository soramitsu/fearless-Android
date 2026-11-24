package jp.co.soramitsu.wallet.impl.presentation

import android.app.Application
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.flags.FragmentGetContextFix
import dagger.hilt.android.internal.builders.FragmentComponentBuilder
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories
import dagger.hilt.android.internal.managers.FragmentComponentManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Hosts `@AndroidEntryPoint` fragments on the JVM without a generated Hilt graph: the test supplies the fragment's
 * view models (placed in the fragment's own ViewModelStore when Hilt would inject it) and any `@Inject` fields.
 * Tests using it run with `@Config(application = StubHiltApplication::class)`.
 */
internal object HiltFragmentHost {

    private const val VIEW_MODEL_KEY = "androidx.lifecycle.ViewModelProvider.DefaultKey:"

    @Volatile
    private var fragmentComponent: Any? = null

    /** The component the host activity hands to fragments; only valid while a fragment is being hosted. */
    fun component(): Any = checkNotNull(fragmentComponent) { "No fragment is being hosted" }

    /**
     * Adds [fragment] (or shows it, for a dialog fragment) in a fresh themed activity. [viewModels] are returned for
     * the fragment's `by viewModels()` properties of their exact classes; [inject] fills `@Inject` fields.
     */
    fun <F : Fragment> host(
        fragment: F,
        viewModels: Map<Class<out ViewModel>, ViewModel>,
        inject: (F) -> Unit = {}
    ): ActivityController<StubHiltActivity> {
        fragmentComponent = fragmentComponent(fragment, viewModels, inject)
        val controller = Robolectric.buildActivity(StubHiltActivity::class.java)
        controller.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
        controller.setup()
        val manager = controller.get().supportFragmentManager
        if (fragment is DialogFragment) {
            fragment.show(manager, TAG)
            manager.executePendingTransactions()
        } else {
            manager.beginTransaction().add(android.R.id.content, fragment, TAG).commitNow()
        }
        ShadowLooper.idleMainLooper()
        return controller
    }

    /** Removes the hosted fragment and destroys its activity. */
    fun close(controller: ActivityController<StubHiltActivity>?) {
        controller ?: return
        runCatching {
            val manager = controller.get().supportFragmentManager
            manager.findFragmentByTag(TAG)?.let { manager.beginTransaction().remove(it).commitNowAllowingStateLoss() }
        }
        runCatching { controller.pause().stop().destroy() }
        ShadowLooper.idleMainLooper()
        fragmentComponent = null
    }

    const val TAG = "hosted"

    private fun <F : Fragment> fragmentComponent(
        fragment: F,
        viewModels: Map<Class<out ViewModel>, ViewModel>,
        inject: (F) -> Unit
    ): Any {
        val loader = fragment.javaClass.classLoader
        val injector = Class.forName(fragment.javaClass.name + "_GeneratedInjector", false, loader)
        val factories = internalFactoryFactory()
        lateinit var proxy: Any
        val handler = InvocationHandler { self, method, args ->
            objectMethod(self, method, args) ?: when {
                method.name == "fragmentComponentBuilder" || method.name == "fragment" || method.name == "build" -> proxy
                method.name == "getHiltInternalFactoryFactory" -> factories
                method.name.startsWith("inject") -> {
                    @Suppress("UNCHECKED_CAST")
                    val target = args!![0] as F
                    viewModels.forEach { (type, model) -> target.viewModelStore.put(VIEW_MODEL_KEY + type.canonicalName, model) }
                    inject(target)
                    null
                }
                else -> error("Unexpected Hilt component call ${method.name}")
            }
        }
        proxy = Proxy.newProxyInstance(
            loader,
            arrayOf(
                GeneratedComponent::class.java,
                FragmentComponentManager.FragmentComponentBuilderEntryPoint::class.java,
                FragmentComponentBuilder::class.java,
                FragmentComponent::class.java,
                DefaultViewModelFactories.FragmentEntryPoint::class.java,
                injector
            ),
            handler
        )
        return proxy
    }

    private fun internalFactoryFactory(): DefaultViewModelFactories.InternalFactoryFactory {
        val builder = Proxy.newProxyInstance(
            ViewModelComponentBuilder::class.java.classLoader,
            arrayOf(ViewModelComponentBuilder::class.java)
        ) { self, method, args -> objectMethod(self, method, args) ?: error("Hosted fragments get their view models from the test") }
        val constructor = DefaultViewModelFactories.InternalFactoryFactory::class.java
            .getDeclaredConstructor(Map::class.java, ViewModelComponentBuilder::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(emptyMap<Class<*>, Boolean>(), builder)
    }

    internal fun objectMethod(self: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
        "equals" -> if (method.parameterCount == 1) self === args?.get(0) else null
        "hashCode" -> if (method.parameterCount == 0) System.identityHashCode(self) else null
        "toString" -> if (method.parameterCount == 0) "HiltStubComponent" else null
        else -> null
    }
}

/** Application answering Hilt's application-level entry points (the fragment context-fix flag) without a graph. */
class StubHiltApplication : Application(), GeneratedComponentManager<Any> {
    private val component: Any by lazy {
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(GeneratedComponent::class.java, FragmentGetContextFix.FragmentGetContextFixEntryPoint::class.java)
        ) { self, method, args ->
            HiltFragmentHost.objectMethod(self, method, args) ?: when (method.name) {
                "getDisableFragmentGetContextFix" -> emptySet<Boolean>()
                else -> error("Unexpected application entry point ${method.name}")
            }
        }
    }

    override fun generatedComponent(): Any = component
}

/** Activity standing in for an `@AndroidEntryPoint` activity: it hands fragments the test's stub component. */
class StubHiltActivity : FragmentActivity(), GeneratedComponentManager<Any> {
    override fun generatedComponent(): Any = HiltFragmentHost.component()
}
