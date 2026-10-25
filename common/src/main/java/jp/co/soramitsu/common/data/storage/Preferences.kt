package jp.co.soramitsu.common.data.storage

import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.flow.Flow

typealias InitialValueProducer<T> = suspend () -> T

interface Preferences {
    /** Stable in-process backing-store identity across wrappers, for acknowledged-write failure fences. */
    val durableStorageIdentity: Any get() = this

    fun contains(field: String): Boolean

    /**
     * Returns whether any stored preference key begins with [prefix].
     *
     * This exposes key names only, never values. Wallet id allocation uses it
     * to avoid reusing an id that still owns orphaned encrypted material.
     */
    fun hasKeyWithPrefix(prefix: String): Boolean {
        error("Preference key-prefix inspection is unavailable")
    }

    /**
     * Visits matching names from a storage snapshot without decrypting values or
     * collecting a bounded global inventory. Oversized names are preserved and
     * skipped independently. Callback failures propagate; callbacks may mutate
     * storage without invalidating this pass's snapshot.
     */
    fun forEachKeyWithPrefixes(
        prefixes: Set<String>,
        maxKeyBytes: Int,
        action: (String) -> Unit
    ) {
        error("Preference key visitation is unavailable")
    }

    /**
     * Returns exact stored key names beginning with one of [prefixes].
     *
     * Implementations expose names only and fail rather than truncate when
     * either the result count or combined UTF-8 byte size exceeds its
     * caller-supplied bound. Names larger than [maxKeyBytes] are skipped unless
     * [failOnOversizedMatch] is requested for a security-sensitive namespace.
     */
    fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean = false
    ): Set<String> {
        error("Preference key-name inspection is unavailable")
    }

    fun putString(field: String, value: String?)

    fun getString(field: String, defaultValue: String): String

    fun getString(field: String): String?

    fun pinPreferenceSnapshot(field: String = PinPreferenceSnapshot.PIN_KEY): PinPreferenceSnapshot {
        PinPreferenceSnapshot.requirePinKey(field)
        return PinPreferenceSnapshot(getString(field))
    }

    /** One exact-value CAS retains a damaged PIN and publishes its replacement. */
    fun replacePinDurably(
        expected: PinPreferenceSnapshot,
        newCiphertext: String,
        archiveKey: String?
    ): Boolean = synchronized(this) {
        if (!pinPreferenceSnapshot().matches(expected)) return@synchronized false
        archiveKey?.let {
            PinPreferenceSnapshot.requirePinKey(it)
            require(it != PinPreferenceSnapshot.PIN_KEY && expected.exists)
            val archived = pinPreferenceSnapshot(it)
            if (archived.exists && !archived.matches(expected)) return@synchronized false
        }
        val values = mutableMapOf(PinPreferenceSnapshot.PIN_KEY to newCiphertext)
        archiveKey?.let { values[it] = checkNotNull(expected.ciphertext) }
        check(replaceStringsDurably(values, emptySet())) { "PIN replacement was not durable" }
        check(values.all { (key, value) -> getString(key) == value }) { "PIN read-back did not match" }
        true
    }

    fun putStringSet(field: String, value: Set<String>?)

    fun getStringSet(field: String, defaultValue: Set<String>): Set<String>

    fun putBoolean(field: String, value: Boolean)

    fun getBoolean(field: String, defaultValue: Boolean): Boolean

    fun putInt(field: String, value: Int)

    fun getInt(field: String, defaultValue: Int): Int

    fun putLong(field: String, value: Long)

    fun getLong(field: String, defaultValue: Long): Long

    fun getCurrentLanguage(): Language?

    fun saveCurrentLanguage(languageIsoCode: String)

    fun removeField(field: String)

    /**
     * Atomically persists a related set of string replacements before returning.
     *
     * This is intentionally separate from the normal asynchronous preference
     * helpers. Database migrations must not commit a schema version that refers
     * to preference-backed wallet material which has not reached durable storage.
     */
    fun replaceStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ): Boolean

    fun stringFlow(
        field: String,
        initialValueProducer: InitialValueProducer<String>? = null
    ): Flow<String?>

    fun stringSetFlow(
        field: String,
        initialValueProducer: InitialValueProducer<Set<String>>? = null
    ): Flow<Set<String>>

    fun intFlow(field: String, initialValue: Int): Flow<Int>

    fun booleanFlow(field: String, initialValue: Boolean): Flow<Boolean>
}
