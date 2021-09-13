package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import jp.co.soramitsu.core.model.Language
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PreferencesImpl(
    private val sharedPreferences: SharedPreferences
) : Preferences {

    companion object {
        private const val PREFS_SELECTED_LANGUAGE = "selected_language"
    }

    override fun contains(field: String) = sharedPreferences.contains(field)

    override fun putString(field: String, value: String?) {
        sharedPreferences.edit().putString(field, value).apply()
    }

    override fun getString(field: String, defaultValue: String): String {
        return sharedPreferences.getString(field, defaultValue) ?: defaultValue
    }

    override fun getString(field: String): String? {
        return sharedPreferences.getString(field, null)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun stringFlow(field: String): Flow<String?> = callbackFlow {
        send(getString(field))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == field) {
                offer(getString(field))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
