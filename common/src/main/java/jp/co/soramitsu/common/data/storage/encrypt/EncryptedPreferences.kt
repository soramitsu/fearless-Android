package jp.co.soramitsu.common.data.storage.encrypt

interface EncryptedPreferences {

    fun putEncryptedString(field: String, value: String)

    fun getDecryptedString(field: String): String?
}