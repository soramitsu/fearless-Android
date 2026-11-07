package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import org.bouncycastle.util.encoders.Hex

/**
 * The device factor of the PIN wrap: a secret mixed into the PIN's slow hash
 * so that guessing the PIN needs the device's master key, which a copy of the
 * keyring records or the app's files does not have.
 */
interface WalletKeyringDeviceFactor {

    /** Creates a new factor under a new alias and returns the alias. */
    fun create(): String

    /**
     * HMAC-SHA256 of [data] under the factor [alias]. Throws
     * [WalletKeyringDeviceFactorLostException] when the factor is gone or no
     * longer authenticates; an unavailable device key propagates as is.
     */
    fun hmac(alias: String, data: ByteArray): ByteArray

    /** Whether a value is stored under [alias]; it says nothing about whether it still decrypts. */
    fun exists(alias: String): Boolean
}

/** The factor [alias] is gone or corrupt; the PIN wrap that names it can never open again. */
class WalletKeyringDeviceFactorLostException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * The factor as 32 random bytes kept under the existing device key M, in the
 * same preferences as every wallet secret, so it adds no new key whose loss
 * alone could strand sealed values: it is lost only with M, and then every
 * sealed value (inside M's layer) is unreadable too, exactly as before 4.3.2.
 *
 * A copy of the app's files without M cannot guess PINs. Code running as the
 * app can unwrap M and guess offline at the Argon2id cost per guess, so a
 * longer PIN is stronger; no hardware binding is claimed.
 *
 * It writes through [Preferences] and [EncryptionUtil] directly, never through
 * the encrypted preferences, whose sealer depends on the keyring.
 */
class MasterKeyBoundKeyringFactor(
    private val preferences: Preferences,
    private val encryptionUtil: EncryptionUtil,
    private val random: SecureRandom = SecureRandom()
) : WalletKeyringDeviceFactor {

    override fun create(): String {
        val alias = ALIAS_PREFIX + Hex.toHexString(ByteArray(ALIAS_RANDOM_BYTES).also(random::nextBytes))
        check(!preferences.contains(alias)) { "A keyring factor alias is already in use" }
        val factor = ByteArray(FACTOR_BYTES).also(random::nextBytes)
        try {
            val encoded = Base64.getEncoder().encodeToString(factor)
            val ciphertext = encryptionUtil.encrypt(encoded)
            check(ciphertext.isNotEmpty() && encryptionUtil.isModernCiphertext(ciphertext)) {
                "The keyring factor was not encrypted under the device key"
            }
            check(preferences.replaceStringsDurably(mapOf(alias to ciphertext), emptySet())) {
                "The keyring factor was not stored durably"
            }
            try {
                val stored = preferences.getString(alias)
                check(stored == ciphertext && encryptionUtil.decrypt(stored) == encoded) {
                    "The keyring factor does not read back"
                }
            } catch (failure: Exception) {
                // Nothing refers to the new alias yet: take back exactly what was written.
                if (preferences.getString(alias) == ciphertext) preferences.replaceStringsDurably(emptyMap(), setOf(alias))
                throw failure
            }
            return alias
        } finally {
            factor.fill(0)
        }
    }

    override fun hmac(alias: String, data: ByteArray): ByteArray {
        val stored = try {
            preferences.getString(alias)
        } catch (failure: ClassCastException) {
            throw WalletKeyringDeviceFactorLostException("The keyring factor has the wrong type", failure)
        } ?: throw WalletKeyringDeviceFactorLostException("The keyring factor is missing")
        // An unavailable device key throws WalletSecureStorageUnavailableException here, unchanged:
        // the PIN screen then asks to try again, never reports a wrong PIN or an unusable wrap.
        val encoded = encryptionUtil.decrypt(stored)
        val factor = try {
            Base64.getDecoder().decode(encoded)
        } catch (failure: IllegalArgumentException) {
            throw WalletKeyringDeviceFactorLostException("The keyring factor does not decode", failure)
        }
        try {
            // decrypt() answers "" for a ciphertext that does not authenticate under the device key.
            if (factor.size != FACTOR_BYTES) {
                throw WalletKeyringDeviceFactorLostException("The keyring factor does not authenticate under the device key")
            }
            return Mac.getInstance(MAC).run {
                init(SecretKeySpec(factor, MAC))
                doFinal(data)
            }
        } finally {
            factor.fill(0)
        }
    }

    override fun exists(alias: String): Boolean = preferences.contains(alias)

    companion object {
        /** Kept under the keyring's namespace, which no pass that walks wallet secrets visits. */
        const val ALIAS_PREFIX = "${WalletSecretKeyring.KEY_PREFIX}factor:"
        private const val ALIAS_RANDOM_BYTES = 8
        private const val FACTOR_BYTES = 32
        private const val MAC = "HmacSHA256"
    }
}
