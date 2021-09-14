package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences

class HashMapEncryptedPreferences : EncryptedPreferences {
    private val delegate = mutableMapOf<String, String>()

    override fun putEncryptedString(field: String, value: String) {
        delegate[field] = value
    }

    override fun getDecryptedString(field: String): String? = delegate[field]
}
