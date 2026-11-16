package jp.co.soramitsu.wallet.impl.di

import jp.co.soramitsu.common.data.network.NetworkApiCreator
import okhttp3.OkHttpClient
import io.mockk.mockkClass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import jp.co.soramitsu.wallet.impl.data.buyToken.CoinbaseProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.MoonPayProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.RampProvider
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every Hilt provider of the wallet feature builds its object from (relaxed) collaborators and returns the type it
 * declares. A provider that throws here fails the app's object graph at start-up.
 */
@RunWith(RobolectricTestRunner::class)
class WalletFeatureModuleRobolectricTest {

    private val module = WalletFeatureModule()

    private val providers: List<Method> = WalletFeatureModule::class.java.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("provide") }
        .sortedBy { it.name }

    @Test
    fun everyProviderReturnsItsDeclaredType() {
        val failures = mutableListOf<String>()
        providers.forEach { method ->
            val result = try {
                method.invoke(module, *method.parameterTypes.map(::argumentFor).toTypedArray())
            } catch (error: InvocationTargetException) {
                failures += "${method.name}: ${error.targetException}"
                return@forEach
            }
            if (result == null || !boxed(method.returnType).isInstance(result)) {
                failures += "${method.name}: returned $result"
            }
        }

        assertTrue("providers found: ${providers.size}", providers.size > 40)
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun buyIntegrationRegistersTheThreeProviders() {
        val registry = module.provideBuyTokenIntegration()

        assertNotNull(registry)
        val names: List<Class<*>> = registry.availableProviders.map { it.javaClass }
        assertTrue(names.toString(), names.containsAll(listOf(RampProvider::class.java, MoonPayProvider::class.java, CoinbaseProvider::class.java)))
    }

    @Test
    fun singletonStateProvidersCreateFreshObjects() {
        assertTrue(module.provideSendSharedState() is SendSharedState)
        assertTrue(module.provideHistoryFiltersProvider() is HistoryFiltersProvider)
        assertTrue(module.provideSendSharedState() !== module.provideSendSharedState())
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        else -> type
    }

    private fun argumentFor(type: Class<*>): Any? = when {
        type == NetworkApiCreator::class.java -> NetworkApiCreator(OkHttpClient(), "https://example.invalid/")
        type == String::class.java -> ""
        type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> false
        type == java.lang.Integer.TYPE || type == java.lang.Integer::class.java -> 0
        type == java.lang.Long.TYPE || type == java.lang.Long::class.java -> 0L
        type.isEnum -> type.enumConstants.first()
        else -> mockkClass(type.kotlin, relaxed = true)
    }
}
