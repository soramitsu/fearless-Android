package jp.co.soramitsu.walletconnect.impl.data

import android.content.Context
import android.content.SharedPreferences
import java.security.KeyStore

/** The Android Keystore entries the removed SDK created, behind a seam the tests replace. */
interface LegacyKeyStore {
    fun containsAlias(alias: String): Boolean

    fun deleteEntry(alias: String)
}

class AndroidLegacyKeyStore : LegacyKeyStore {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) } }

    override fun containsAlias(alias: String): Boolean = keyStore.containsAlias(alias)

    override fun deleteEntry(alias: String) = keyStore.deleteEntry(alias)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}

/**
 * Deletes what the removed Reown/WalletConnect SDK (4.3.1 and earlier) kept: its SQLCipher
 * databases, its encrypted preferences and their Keystore keys, which held WalletConnect session
 * keys. Only these exact names are touched; wallet data (`fearless_prefs`, `key_alias`, `app.db`)
 * never is. Runs until it once completes, then never again.
 */
class LegacyWalletConnectSdkCleanup(
    private val markers: SharedPreferences,
    private val deleteDatabase: (String) -> Unit,
    private val deleteSharedPreferences: (String) -> Unit,
    private val keyStore: LegacyKeyStore
) {
    constructor(context: Context, markers: SharedPreferences) : this(
        markers = markers,
        deleteDatabase = { name -> context.deleteDatabase(name) },
        deleteSharedPreferences = { name -> context.deleteSharedPreferences(name) },
        keyStore = AndroidLegacyKeyStore()
    )

    /** True when everything is gone (now or earlier); false when something failed and will be retried. */
    fun run(): Boolean {
        if (markers.getBoolean(MARKER, false)) return true
        val completed = runCatching {
            LEGACY_DATABASES.forEach(deleteDatabase)
            LEGACY_PREFERENCES.forEach(deleteSharedPreferences)
            LEGACY_KEY_ALIASES.forEach { alias -> if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
        }.isSuccess
        return completed && markers.edit().putBoolean(MARKER, true).commit()
    }

    companion object {
        const val MARKER = "legacy_sdk_removed.v1"
        val LEGACY_DATABASES = listOf("WalletConnectAndroidCore.db", "WalletConnectV2.db", "WalletConnectV2_notify.db")
        val LEGACY_PREFERENCES = listOf("wc_key_store", "db_key_store")
        val LEGACY_KEY_ALIASES = listOf("wc_keystore_key", "_wc_db_key_")
    }
}
