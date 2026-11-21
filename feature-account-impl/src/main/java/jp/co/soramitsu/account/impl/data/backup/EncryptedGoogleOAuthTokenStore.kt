package jp.co.soramitsu.account.impl.data.backup

import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences

/**
 * Keeps the Google Drive sign-in (its refresh token) in the app's encrypted preferences, under a key
 * outside every wallet-secret prefix so wallet recovery and quarantine never touch it.
 */
class EncryptedGoogleOAuthTokenStore(
    private val preferences: EncryptedPreferences
) : GoogleOAuthTokenStore {

    // An unreadable value reads as signed out; signing in again replaces it.
    override fun read(): String? = runCatching { preferences.getDecryptedString(KEY) }.getOrNull()

    override fun write(value: String) {
        preferences.replaceEncryptedStringsDurably(valuesToPut = mapOf(KEY to value), keysToRemove = emptySet())
    }

    override fun clear() {
        preferences.replaceEncryptedStringsDurably(valuesToPut = emptyMap(), keysToRemove = setOf(KEY))
    }

    companion object {
        const val KEY = "google_drive_oauth_session_v1"
    }
}
