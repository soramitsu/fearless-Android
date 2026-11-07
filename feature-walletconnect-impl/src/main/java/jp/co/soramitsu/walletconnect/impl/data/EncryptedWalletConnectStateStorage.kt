package jp.co.soramitsu.walletconnect.impl.data

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage

/** Seals and opens the WalletConnect state; [open] returns null when the sealed value is corrupt. */
interface WalletConnectStateCipher {
    fun seal(plaintext: String): String

    /** The plaintext, or null when [sealed] is corrupt. Throws when the key is unavailable right now. */
    fun open(sealed: String): String?
}

/**
 * The app's wallet encryption key (AES-256-GCM, wrapped by the Android Keystore), the one that
 * protects `fearless_prefs`. WalletConnect state is sealed with it but kept in a preferences file of
 * its own, so WalletConnect never writes into wallet-secret storage.
 */
class EncryptionUtilStateCipher(private val encryptionUtil: EncryptionUtil) : WalletConnectStateCipher {
    override fun seal(plaintext: String): String {
        val sealed = encryptionUtil.encrypt(plaintext)
        check(encryptionUtil.isModernCiphertext(sealed)) { "WalletConnect state could not be encrypted" }
        return sealed
    }

    override fun open(sealed: String): String? = encryptionUtil.decrypt(sealed).takeIf { it.isNotEmpty() }
}

/**
 * [WalletConnectStateStorage] in the private preferences file [FILE_NAME]: one encrypted value,
 * never plaintext. A corrupt value reads as nothing stored, so the next change replaces it; a key
 * that cannot be used right now throws and leaves the value as it is.
 */
class EncryptedWalletConnectStateStorage(
    private val preferences: SharedPreferences,
    private val cipher: WalletConnectStateCipher
) : WalletConnectStateStorage {

    override fun read(): String? {
        val sealed = preferences.getString(KEY_STATE, null) ?: return null
        return cipher.open(sealed)
    }

    override fun write(json: String) {
        val sealed = cipher.seal(json)
        check(preferences.edit().putString(KEY_STATE, sealed).commit()) { "WalletConnect state could not be stored" }
    }

    override fun clear() {
        check(preferences.edit().remove(KEY_STATE).commit()) { "WalletConnect state could not be removed" }
    }

    companion object {
        const val FILE_NAME = "walletconnect_v2"
        const val KEY_STATE = "state.v1"
    }
}
