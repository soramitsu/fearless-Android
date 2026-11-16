package jp.co.soramitsu.staking.impl.presentation

import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.internal.builders.FragmentComponentBuilder
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories
import dagger.hilt.android.internal.lifecycle.HiltViewModelFactory
import dagger.hilt.android.internal.managers.FragmentComponentManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.inject.Provider
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Hosts `@AndroidEntryPoint` fragments in plain Robolectric tests. The fake Hilt components hand each fragment the
 * view model the test supplies and run the test's field injection, so the real fragment code inflates its layout,
 * binds its views and subscribes to that view model exactly as in the app.
 */
class HiltHostActivity : FragmentActivity(), GeneratedComponentManager<Any> {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
        super.onCreate(savedInstanceState)
    }

    override fun generatedComponent(): Any = HiltFragmentHarness.activityComponent
}

object HiltFragmentHarness {

    private var viewModels: Map<Class<*>, ViewModel> = emptyMap()
    private var injector: (Fragment) -> Unit = {}

    private fun proxy(interfaces: List<Class<*>>, handler: (Method, Array<Any?>) -> Any?): Any =
        Proxy.newProxyInstance(
            HiltFragmentHarness::class.java.classLoader,
            interfaces.toTypedArray(),
            InvocationHandler { self, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(self)
                    "equals" -> self === args?.get(0)
                    "toString" -> "HiltFragmentHarness.${method.declaringClass.simpleName}"
                    else -> handler(method, args ?: emptyArray())
                }
            }
        )

    private val viewModelComponentBuilder: ViewModelComponentBuilder by lazy {
        lateinit var builder: Any
        builder = proxy(listOf(ViewModelComponentBuilder::class.java)) { method, _ ->
            when (method.name) {
                "build" -> proxy(
                    listOf(
                        ViewModelComponent::class.java,
                        GeneratedComponent::class.java,
                        HiltViewModelFactory.ViewModelFactoriesEntryPoint::class.java
                    )
                ) { inner, _ ->
                    when (inner.name) {
                        "getHiltViewModelMap" -> viewModels.mapValues { (_, vm) -> Provider { vm } }
                        else -> emptyMap<Class<*>, Any>()
                    }
                }
                else -> builder
            }
        }
        builder as ViewModelComponentBuilder
    }

    private fun internalFactoryFactory(): Any {
        val type = DefaultViewModelFactories.InternalFactoryFactory::class.java
        val constructor = type.declaredConstructors.single()
        constructor.isAccessible = true
        val keys: Map<Class<*>, Boolean> = viewModels.keys.associateWith { true }
        return constructor.newInstance(keys, viewModelComponentBuilder)
    }

    private fun fragmentComponent(fragment: Fragment): Any {
        val interfaces = mutableListOf<Class<*>>(
            FragmentComponent::class.java,
            GeneratedComponent::class.java,
            DefaultViewModelFactories.FragmentEntryPoint::class.java
        )
        var type: Class<*>? = fragment.javaClass
        while (type != null && type != Fragment::class.java) {
            runCatching { Class.forName(type.name + "_GeneratedInjector") }.getOrNull()?.let(interfaces::add)
            type = type.superclass
        }
        return proxy(interfaces) { method, args ->
            when {
                method.name == "getHiltInternalFactoryFactory" -> internalFactoryFactory()
                method.name.startsWith("inject") -> {
                    injector(args[0] as Fragment)
                    null
                }
                else -> null
            }
        }
    }

    val activityComponent: Any by lazy {
        proxy(listOf(GeneratedComponent::class.java, FragmentComponentManager.FragmentComponentBuilderEntryPoint::class.java)) { _, _ ->
            var target: Fragment? = null
            lateinit var builder: Any
            builder = proxy(listOf(FragmentComponentBuilder::class.java)) { method, args ->
                when (method.name) {
                    "fragment" -> {
                        target = args[0] as Fragment
                        builder
                    }
                    else -> fragmentComponent(requireNotNull(target))
                }
            }
            builder
        }
    }

    /** Adds [fragment] to a fresh host activity with [viewModel] as its Hilt view model and returns the activity. */
    inline fun <F : Fragment, reified VM : ViewModel> launch(
        fragment: F,
        viewModel: VM,
        arguments: Bundle? = null,
        noinline inject: (F) -> Unit = {}
    ): HiltHostActivity = launchWith(fragment, VM::class.java, viewModel, arguments, inject)

    fun <F : Fragment> launchWith(
        fragment: F,
        viewModelClass: Class<out ViewModel>,
        viewModel: ViewModel,
        arguments: Bundle?,
        inject: (F) -> Unit
    ): HiltHostActivity {
        viewModels = mapOf(viewModelClass to viewModel)
        @Suppress("UNCHECKED_CAST")
        injector = { inject(it as F) }
        arguments?.let { fragment.arguments = it }
        val activity = Robolectric.buildActivity(HiltHostActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }
}
