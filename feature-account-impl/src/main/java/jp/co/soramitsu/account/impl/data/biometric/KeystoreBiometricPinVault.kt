package jp.co.soramitsu.account.impl.data.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault.UnlockCipher
import jp.co.soramitsu.common.data.storage.Preferences

/**
 * [BiometricPinVault] on an RSA-2048 OAEP key in the Android Keystore.
 *
 * The public half seals the PIN without a prompt, so enrollment needs no biometric. The private
 * half needs a strong biometric for every use and is invalidated by any new biometric enrollment.
 * Only the sealed PIN is stored, in ordinary preferences: without the hardware-held private key
 * it reveals nothing.
 */
@Singleton
class KeystoreBiometricPinVault @Inject constructor(
    private val preferences: Preferences
) : BiometricPinVault {

    override fun isEnrolled(): Boolean = preferences.getString(SEALED_PIN_KEY) != null && keyExists()

    override fun enroll(pin: String) {
        require(isWalletPin(pin)) { "Not a wallet PIN" }
        val keyStore = keyStore()
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        val generated = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).run {
            initialize(keySpec())
            generateKeyPair()
        }
        // A plain copy of the public key keeps the Keystore provider out of the sealing step.
        val publicKey = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(X509EncodedKeySpec(generated.public.encoded))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, publicKey, OAEP) }
        val plain = pin.toByteArray(Charsets.US_ASCII)
        try {
            preferences.putString(SEALED_PIN_KEY, Base64.getEncoder().encodeToString(cipher.doFinal(plain)))
        } finally {
            plain.fill(0)
        }
        preferences.putBoolean(INVALIDATED_KEY, false)
    }

    override fun prepareUnlock(): UnlockCipher {
        if (preferences.getString(SEALED_PIN_KEY) == null) return UnlockCipher.Unavailable
        return try {
            // A key that vanished while its sealed PIN remains was deleted by the platform, for
            // example when the screen lock was removed. Treat it like a biometric change.
            val key = keyStore().getKey(ALIAS, null) as? PrivateKey ?: return invalidate()
            UnlockCipher.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, OAEP) })
        } catch (_: KeyPermanentlyInvalidatedException) {
            invalidate()
        } catch (_: GeneralSecurityException) {
            UnlockCipher.Unavailable
        }
    }

    override fun decryptPin(authenticated: Cipher): String? {
        val sealed = preferences.getString(SEALED_PIN_KEY) ?: return null
        return try {
            val plain = authenticated.doFinal(Base64.getDecoder().decode(sealed))
            try {
                String(plain, Charsets.US_ASCII).takeIf(::isWalletPin)
            } finally {
                plain.fill(0)
            }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    override fun clear() {
        preferences.removeField(SEALED_PIN_KEY)
        try {
            keyStore().run { if (containsAlias(ALIAS)) deleteEntry(ALIAS) }
        } catch (_: GeneralSecurityException) {
            // Nothing can use a key without its sealed PIN, so a failed delete leaves nothing usable.
        }
    }

    override fun wasInvalidated(): Boolean = preferences.getBoolean(INVALIDATED_KEY, false)

    override fun acknowledgeInvalidation() {
        preferences.putBoolean(INVALIDATED_KEY, false)
    }

    private fun invalidate(): UnlockCipher {
        clear()
        preferences.putBoolean(INVALIDATED_KEY, true)
        return UnlockCipher.Invalidated
    }

    private fun keyExists(): Boolean = try {
        keyStore().containsAlias(ALIAS)
    } catch (_: GeneralSecurityException) {
        false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keySpec(): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_SIZE)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            // -1: every use needs a fresh biometric through the prompt's CryptoObject.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return builder.build()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "fearless_biometric_pin_v1"
        private const val KEY_SIZE = 2048
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        internal const val SEALED_PIN_KEY = "biometric_pin_vault_v1"
        internal const val INVALIDATED_KEY = "biometric_pin_vault_invalidated_v1"

        // MGF1 stays on SHA-1: that is what every Android Keystore version accepts for OAEP.
        private val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
