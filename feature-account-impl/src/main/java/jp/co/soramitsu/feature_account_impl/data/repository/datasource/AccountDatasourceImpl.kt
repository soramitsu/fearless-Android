package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.AuthType

class AccountDatasourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences
) : AccountDatasource {

    companion object {
        private const val PREFS_AUTH_TYPE = "auth_type"
        private const val PREFS_SELECTED_LANGUAGE = "selected_language"
        private const val PREFS_PIN_CODE = "pin_code"
    }

    override fun saveAuthType(authType: AuthType) {
        preferences.putString(PREFS_AUTH_TYPE, authType.toString())
    }

    override fun getAuthType(): AuthType {
        val savedValue = preferences.getString(PREFS_AUTH_TYPE)
        return if (savedValue == null) {
            AuthType.PINCODE
        } else {
            AuthType.valueOf(savedValue)
        }
    }

    override fun saveSelectedLanguage(language: String) {
        preferences.putString(PREFS_SELECTED_LANGUAGE, language)
    }

    override fun getSelectedLanguage(): String? {
        return preferences.getString(PREFS_SELECTED_LANGUAGE)
    }

    override fun savePinCode(pinCode: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pinCode)
    }

    override fun getPinCode(): String? {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }
}