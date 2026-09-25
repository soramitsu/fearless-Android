package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.common.data.storage.InitialValueProducer
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class InMemoryPreferences : Preferences {
    private val values = mutableMapOf<String, Any?>()

    override fun contains(field: String) = field in values
    override fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean
    ): Set<String> {
        var totalBytes = 0
        return values.keys.asSequence()
            .filter { key -> prefixes.any(key::startsWith) }
            .filter { key ->
                val size = key.encodeToByteArray().size
                if (size > maxKeyBytes) {
                    check(!failOnOversizedMatch)
                    false
                } else {
                    totalBytes += size
                    check(totalBytes <= maxTotalKeyBytes)
                    true
                }
            }
            .take(maxResultCount + 1)
            .toSet()
            .also { check(it.size <= maxResultCount) }
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
