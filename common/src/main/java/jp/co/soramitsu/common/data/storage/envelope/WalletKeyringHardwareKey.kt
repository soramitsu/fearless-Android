package jp.co.soramitsu.common.data.storage.envelope

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import org.bouncycastle.util.encoders.Hex

/**
 * K_hw: a non-exportable HMAC-SHA256 key in the Android Keystore with no user
 * authentication requirement. It is mixed into the PIN wrap, so PIN guesses
 * can only run on this phone, at the PIN KDF's cost per guess.
 */
interface WalletKeyringHardwareKey {

    /** Generates a new key under a new alias and returns the alias. */
    fun create(): String

    /** HMAC-SHA256 of [data] under [alias]. */
    fun hmac(alias: String, data: ByteArray): ByteArray

    /** False only when the Keystore answers that [alias] is gone; a Keystore outage throws. */
    fun exists(alias: String): Boolean
}

/** [alias] is gone or permanently unusable; the PIN wrap that names it can never open again. */
class WalletKeyringHardwareKeyLostException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class AndroidKeystoreWalletKeyringHardwareKey(
    private val random: SecureRandom = SecureRandom()
) : WalletKeyringHardwareKey {

    override fun create(): String {
        val alias = ALIAS_PREFIX + Hex.toHexString(ByteArray(ALIAS_RANDOM_BYTES).also(random::nextBytes))
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
        check(exists(alias)) { "The keyring hardware key was not stored" }
        return alias
    }

    override fun hmac(alias: String, data: ByteArray): ByteArray {
        val key = try {
            keyStore().getKey(alias, null) as? SecretKey
        } catch (failure: UnrecoverableKeyException) {
            throw WalletKeyringHardwareKeyLostException("The keyring hardware key cannot be recovered", failure)
        } ?: throw WalletKeyringHardwareKeyLostException("The keyring hardware key is missing")
        return try {
            Mac.getInstance(MAC).run {
                init(key)
                doFinal(data)
            }
        } catch (failure: KeyPermanentlyInvalidatedException) {
            throw WalletKeyringHardwareKeyLostException("The keyring hardware key was invalidated", failure)
        }
    }

    override fun exists(alias: String): Boolean = keyStore().containsAlias(alias)

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MAC = "HmacSHA256"
        const val ALIAS_PREFIX = "fearless_wallet_keyring_hmac_v1_"
        const val ALIAS_RANDOM_BYTES = 8
    }
}
