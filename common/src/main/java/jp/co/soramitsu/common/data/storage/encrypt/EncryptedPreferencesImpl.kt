package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.Preferences

class EncryptedPreferencesImpl(
    private val preferences: Preferences,
    private val encryptionUtil: EncryptionUtil
) : EncryptedPreferences {

    override fun putEncryptedString(field: String, value: String) {
        preferences.putString(field, encryptionUtil.encrypt(value))
    }

    override fun getDecryptedString(field: String): String? {
        val encryptedString = preferences.getString(field) ?: return null
        val decrypted = encryptionUtil.decrypt(encryptedString)

        if (decrypted.isNotEmpty() && !encryptionUtil.isModernCiphertext(encryptedString)) {
            preferences.putString(field, encryptionUtil.encrypt(decrypted))
        }

        return decrypted
    }

    override fun hasKey(field: String): Boolean {
        return preferences.contains(field)
    }

    override fun removeKey(field: String) {
        preferences.removeField(field)
    }
}
