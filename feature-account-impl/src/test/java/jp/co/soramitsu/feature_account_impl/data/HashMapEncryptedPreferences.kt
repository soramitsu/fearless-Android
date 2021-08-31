package jp.co.soramitsu.feature_account_impl.data

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences

class HashMapEncryptedPreferences : EncryptedPreferences {
    private val delegate = mutableMapOf<String, String>()

    override fun putEncryptedString(field: String, value: String) {
        delegate[field] = value
    }

    override fun getDecryptedString(field: String): String? = delegate[field]
}
