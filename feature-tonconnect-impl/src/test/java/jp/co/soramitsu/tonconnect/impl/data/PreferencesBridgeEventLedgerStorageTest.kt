package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PreferencesBridgeEventLedgerStorageTest {

    @Test
    fun shouldWriteDurablyAndReadBack() {
        val values = mutableMapOf<String, String>()
        val storage = PreferencesBridgeEventLedgerStorage(preferences(values))

        assertNull(storage.read())
        assertTrue(storage.write("v1"))

        assertEquals("v1", storage.read())
        assertEquals(mapOf(PreferencesBridgeEventLedgerStorage.KEY to "v1"), values)
    }

    @Test
    fun shouldReportFailure_whenTheCommitFailsOrDoesNotReadBack() {
        assertFalse(PreferencesBridgeEventLedgerStorage(preferences(mutableMapOf(), commits = false)).write("v1"))
        assertFalse(PreferencesBridgeEventLedgerStorage(preferences(mutableMapOf(), persists = false)).write("v1"))
    }

    @Test
    fun shouldUseAKeyOutsideTheTonConnectSecretNamespaces() {
        val key = PreferencesBridgeEventLedgerStorage.KEY

        assertFalse(key.startsWith("TON_CONNECT_"))
        assertFalse(TonConnectStorageKeys.isLegacyKey(key))
        assertFalse(TonConnectStorageKeys.isScopedKey(key))
    }

    private fun preferences(
        values: MutableMap<String, String>,
        commits: Boolean = true,
        persists: Boolean = true
    ): Preferences = Proxy.newProxyInstance(
        Preferences::class.java.classLoader,
        arrayOf(Preferences::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0] as String]
            "replaceStringsDurably" -> {
                val toPut = args[0] as Map<*, *>
                val toRemove = args[1] as Set<*>
                if (persists) {
                    toPut.forEach { (key, value) -> values[key as String] = value as String }
                    toRemove.forEach { key -> values.remove(key) }
                }
                commits
            }
            else -> error("${method.name} is not used by the ledger storage")
        }
    } as Preferences
}
