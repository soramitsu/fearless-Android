package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

class PreferencesImpl(
    private val sharedPreferences: SharedPreferences
) : Preferences {
    override val durableStorageIdentity: Any get() = sharedPreferences

    /*
    SharedPreferencesImpl stores listeners in a WeakHashMap,
    meaning listener is subject to GC if it is not kept anywhere else.
    This is not a problem until a stringFlow() call is followed later by shareIn() or stateIn(),
    which cause listener to be GC-ed (TODO - research why).
    To avoid that, store strong references to listeners until corresponding flow is closed.
    */
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    companion object {

        private const val PREFS_SELECTED_LANGUAGE = "selected_language"
        private const val MAX_KEY_PREFIXES = 32
        private const val MAX_KEY_PREFIX_BYTES = 256
        private const val MAX_KEY_QUERY_RESULTS = 8_192
        private const val MAX_PREFERENCE_KEY_BYTES = 1_024
        private const val MAX_KEY_QUERY_TOTAL_BYTES = 1_048_576
    }

    override fun contains(field: String) = sharedPreferences.contains(field)

    override fun hasKeyWithPrefix(prefix: String): Boolean {
        require(prefix.isNotEmpty()) { "A preference key prefix cannot be empty" }
        return sharedPreferences.all.keys.any { it.startsWith(prefix) }
    }

    override fun forEachKeyWithPrefixes(
        prefixes: Set<String>,
        maxKeyBytes: Int,
        action: (String) -> Unit
    ) {
        // Android SharedPreferences already keeps its data in memory. Its
        // public API supplies a snapshot; only names are inspected here, and
        // there is no additional sorted inventory or decrypted-value copy.
        forEachMatchingPreferenceKey(Iterable { sharedPreferences.all.keys.iterator() }, prefixes, maxKeyBytes, action)
    }

    override fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean
    ): Set<String> {
        require(prefixes.isNotEmpty() && prefixes.size <= MAX_KEY_PREFIXES) {
            "The preference key prefix set has an invalid size"
        }
        require(
            prefixes.all {
                it.isNotEmpty() &&
                    it.encodeToByteArray().size <= MAX_KEY_PREFIX_BYTES
            }
        ) {
            "A preference key prefix has an invalid size"
        }
        require(maxResultCount in 1..MAX_KEY_QUERY_RESULTS) {
            "The preference key result bound is invalid"
        }
        require(maxKeyBytes in 1..MAX_PREFERENCE_KEY_BYTES) {
            "The preference key size bound is invalid"
        }
        require(maxTotalKeyBytes in maxKeyBytes..MAX_KEY_QUERY_TOTAL_BYTES) {
            "The preference key total-size bound is invalid"
        }

        var totalKeyBytes = 0
        val result = linkedSetOf<String>()
        sharedPreferences.all.keys.forEach { key ->
            if (prefixes.any(key::startsWith)) {
                val keyBytes = if (key.length > maxKeyBytes) {
                    null
                } else {
                    key.encodeToByteArray().size
                }
                if (keyBytes == null || keyBytes > maxKeyBytes) {
                    check(!failOnOversizedMatch) {
                        "The bounded preference key query matched an oversized key"
                    }
                } else {
                    check(result.size < maxResultCount) {
                        "The bounded preference key query matched too many keys"
                    }
                    check(totalKeyBytes <= maxTotalKeyBytes - keyBytes) {
                        "The bounded preference key query matched too many key bytes"
                    }
                    result += key
                    totalKeyBytes += keyBytes
                }
            }
        }

        return result.toSortedSet()
    }

    override fun putString(field: String, value: String?) {
        sharedPreferences.edit().putString(field, value).apply()
    }

    override fun getString(field: String, defaultValue: String): String {
        return sharedPreferences.getString(field, defaultValue) ?: defaultValue
    }

    override fun putStringSet(field: String, value: Set<String>?) {
        sharedPreferences.edit().putStringSet(field, value).apply()
    }

    override fun getStringSet(field: String, defaultValue: Set<String>): Set<String> {
        return sharedPreferences.getStringSet(field, defaultValue) ?: defaultValue
    }

    override fun getString(field: String): String? {
        return sharedPreferences.getString(field, null)
    }

    override fun pinPreferenceSnapshot(field: String): PinPreferenceSnapshot {
        PinPreferenceSnapshot.requirePinKey(field)
        return PinPreferenceSnapshot(sharedPreferences.all[field])
    }

    @Synchronized
    override fun replacePinDurably(
        expected: PinPreferenceSnapshot,
        newCiphertext: String,
        archiveKey: String?
    ): Boolean {
        if (!pinPreferenceSnapshot(PinPreferenceSnapshot.PIN_KEY).matches(expected)) return false
        archiveKey?.let {
            PinPreferenceSnapshot.requirePinKey(it)
            require(it != PinPreferenceSnapshot.PIN_KEY && expected.exists)
            val archived = pinPreferenceSnapshot(it)
            if (archived.exists && !archived.matches(expected)) return false
        }
        val editor = sharedPreferences.edit()
        if (archiveKey != null) {
            when (val value = expected.rawValue) {
                is String -> editor.putString(archiveKey, value)
                is Int -> editor.putInt(archiveKey, value)
                is Long -> editor.putLong(archiveKey, value)
                is Float -> editor.putFloat(archiveKey, value)
                is Boolean -> editor.putBoolean(archiveKey, value)
                is Set<*> -> {
                    require(value.all { it is String })
                    editor.putStringSet(archiveKey, value.map { it as String }.toSet())
                }
                else -> error("The retained PIN has an unsupported preference type")
            }
        }
        check(editor.putString(PinPreferenceSnapshot.PIN_KEY, newCiphertext).commit()) {
            "PIN replacement was not durable"
        }
        check(sharedPreferences.getString(PinPreferenceSnapshot.PIN_KEY, null) == newCiphertext &&
            (archiveKey == null || pinPreferenceSnapshot(archiveKey).matches(expected))) {
            "PIN read-back did not match"
        }
        return true
    }

    override fun putBoolean(field: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(field, value).apply()
    }

    override fun getBoolean(field: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(field, defaultValue)
    }

    override fun putInt(field: String, value: Int) {
        sharedPreferences.edit().putInt(field, value).apply()
    }

    override fun getInt(field: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(field, defaultValue)
    }

    override fun putLong(field: String, value: Long) {
        sharedPreferences.edit().putLong(field, value).apply()
    }

    override fun getLong(field: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(field, defaultValue)
    }

    override fun getCurrentLanguage(): Language? {
        return if (sharedPreferences.contains(PREFS_SELECTED_LANGUAGE)) {
            Language(sharedPreferences.getString(PREFS_SELECTED_LANGUAGE, "")!!)
        } else {
            null
        }
    }

    override fun saveCurrentLanguage(languageIsoCode: String) {
        sharedPreferences.edit().putString(PREFS_SELECTED_LANGUAGE, languageIsoCode).apply()
    }

    override fun removeField(field: String) {
        sharedPreferences.edit().remove(field).apply()
    }

    override fun replaceStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ): Boolean {
        require(valuesToPut.keys.intersect(keysToRemove).isEmpty()) {
            "A durable preference replacement cannot put and remove the same key"
        }

        val editor = sharedPreferences.edit()
        valuesToPut.forEach { (key, value) -> editor.putString(key, value) }
        keysToRemove.forEach { key -> editor.remove(key) }

        return editor.commit()
    }

    override fun stringFlow(field: String, initialValueProducer: (suspend () -> String)?): Flow<String?> =
        if (initialValueProducer == null) {
            observeString(field)
        } else {
            callbackFlow {
                if (contains(field)) {
                    send(getString(field))
                } else {
                    val initialValue = initialValueProducer?.invoke()

                    putString(field, initialValue)

                    send(initialValue)
                }

                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == field) {
                        trySend(getString(field))
                    }
                }

                listeners.add(listener)
                sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

                awaitClose {
                    listeners.remove(listener)
                    sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
        }

    private fun observeString(field: String): Flow<String?> = callbackFlow {
        val observationLock = Any()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == field || key == null) {
                synchronized(observationLock) {
                    trySend(getString(field))
                }
            }
        }

        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            // Registration precedes observation. Serialize the read and emission with
            // callbacks so an older initial snapshot cannot follow a newer callback.
            synchronized(observationLock) {
                trySend(getString(field))
            }
            awaitClose()
        } finally {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.buffer(Channel.CONFLATED)

    override fun stringSetFlow(
        field: String,
        initialValueProducer: InitialValueProducer<Set<String>>?
    ): Flow<Set<String>> = callbackFlow {
        if (contains(field)) {
            send(getStringSet(field, emptySet()))
        } else {
            val initialValue = initialValueProducer?.invoke() ?: emptySet()

            putStringSet(field, initialValue)

            send(initialValue)
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == field) {
                trySend(getStringSet(field, emptySet()))
            }
        }

        listeners.add(listener)
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            listeners.remove(listener)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun intFlow(
        field: String,
        initialValue: Int
    ): Flow<Int> = callbackFlow {
        if (contains(field)) {
            send(getInt(field, 0))
        } else {
            putInt(field, initialValue)
            send(initialValue)
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == field) {
                trySend(getInt(field, initialValue))
            }
        }

        listeners.add(listener)
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            listeners.remove(listener)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun booleanFlow(
        field: String,
        initialValue: Boolean
    ): Flow<Boolean> = callbackFlow {
        val observationLock = Any()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == field || key == null) {
                synchronized(observationLock) {
                    trySend(getBoolean(field, initialValue))
                }
            }
        }

        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            // Observe only after registration, without initializing over a concurrent choice.
            synchronized(observationLock) {
                trySend(getBoolean(field, initialValue))
            }
            awaitClose()
        } finally {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.buffer(Channel.CONFLATED)
}

var Preferences.appConfig: AppConfigRemote
    get() {
        val minSupportedVersion = getString("MIN_SUPPORTED_VERSION", "3.0.2")
        val excludedVersions = getStringSet("EXCLUDED_VERSIONS", emptySet()).toList()
        return AppConfigRemote(minSupportedVersion, excludedVersions)
    }
    set(value) {
        putString("MIN_SUPPORTED_VERSION", value.minSupportedVersion)
        putStringSet("EXCLUDED_VERSIONS", value.excludedVersions.orEmpty().toSet())
    }
