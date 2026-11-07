package jp.co.soramitsu.testshared

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.storage.InitialValueProducer
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.envelope.WalletKeyringHardwareKey
import jp.co.soramitsu.common.data.storage.envelope.WalletKeyringHardwareKeyLostException
import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Plain preferences in memory, for the wallet keyring records in tests. */
class InMemoryKeyringPreferences : Preferences {
    private val values = linkedMapOf<String, Any?>()

    override fun contains(field: String) = field in values
    override fun hasKeyWithPrefix(prefix: String): Boolean = values.keys.any { it.startsWith(prefix) }
    override fun forEachKeyWithPrefixes(prefixes: Set<String>, maxKeyBytes: Int, action: (String) -> Unit) {
        values.keys.filter { key -> prefixes.any(key::startsWith) }.sorted().forEach(action)
    }
    override fun putString(field: String, value: String?) { values[field] = value }
    override fun getString(field: String, defaultValue: String) = values[field] as? String ?: defaultValue
    override fun getString(field: String) = values[field] as? String
    override fun putStringSet(field: String, value: Set<String>?) { values[field] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(field: String, defaultValue: Set<String>) = values[field] as? Set<String> ?: defaultValue
    override fun putBoolean(field: String, value: Boolean) { values[field] = value }
    override fun getBoolean(field: String, defaultValue: Boolean) = values[field] as? Boolean ?: defaultValue
    override fun putInt(field: String, value: Int) { values[field] = value }
    override fun getInt(field: String, defaultValue: Int) = values[field] as? Int ?: defaultValue
    override fun putLong(field: String, value: Long) { values[field] = value }
    override fun getLong(field: String, defaultValue: Long) = values[field] as? Long ?: defaultValue
    override fun getCurrentLanguage(): Language? = null
    override fun saveCurrentLanguage(languageIsoCode: String) = Unit
    override fun removeField(field: String) { values.remove(field) }
    override fun replaceStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>): Boolean {
        values.putAll(valuesToPut)
        keysToRemove.forEach(values::remove)
        return true
    }
    override fun stringFlow(field: String, initialValueProducer: InitialValueProducer<String>?): Flow<String?> =
        flowOf(getString(field))
    override fun stringSetFlow(field: String, initialValueProducer: InitialValueProducer<Set<String>>?): Flow<Set<String>> =
        flowOf(getStringSet(field, emptySet()))
    override fun intFlow(field: String, initialValue: Int): Flow<Int> = flowOf(getInt(field, initialValue))
    override fun booleanFlow(field: String, initialValue: Boolean): Flow<Boolean> = flowOf(getBoolean(field, initialValue))
}

/** An in-memory K_hw for tests: an HMAC key per alias that [loseAll] can take away. */
class InMemoryKeyringHardwareKey : WalletKeyringHardwareKey {
    private val keys = mutableMapOf<String, ByteArray>()
    private var next = 0

    override fun create(): String {
        val alias = "test_hw_${next++}"
        keys[alias] = ByteArray(32).also(SecureRandom()::nextBytes)
        return alias
    }

    override fun hmac(alias: String, data: ByteArray): ByteArray {
        val key = keys[alias] ?: throw WalletKeyringHardwareKeyLostException("missing")
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }

    override fun exists(alias: String): Boolean = alias in keys

    fun loseAll() = keys.clear()
}
