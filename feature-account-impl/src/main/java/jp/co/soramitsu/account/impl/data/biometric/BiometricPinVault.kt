package jp.co.soramitsu.account.impl.data.biometric

import javax.crypto.Cipher

/**
 * Keeps the wallet PIN encrypted to an Android Keystore key that only a strong (Class 3) biometric
 * can use, and that the platform destroys when a fingerprint or face is added to the phone.
 *
 * A biometric unlock therefore proves what typing the PIN proves. A fingerprint added later, for
 * example by someone who knows the phone's screen lock, cannot unlock the wallet.
 */
interface BiometricPinVault {

    sealed interface UnlockCipher {
        /** A cipher to hand to the BiometricPrompt as its CryptoObject. */
        class Ready(val cipher: Cipher) : UnlockCipher

        /** The phone's biometrics changed after enrollment, so the key is gone for good. */
        object Invalidated : UnlockCipher

        /** Nothing is enrolled, or the Keystore cannot provide the key right now. */
        object Unavailable : UnlockCipher
    }

    fun isEnrolled(): Boolean

    /** Replaces any earlier enrollment with [pin], sealed to a new biometric-bound key. */
    fun enroll(pin: String)

    fun prepareUnlock(): UnlockCipher

    /** Opens the sealed PIN with a cipher the BiometricPrompt has just authenticated, or returns null. */
    fun decryptPin(authenticated: Cipher): String?

    fun clear()

    /** True from the moment the platform invalidated the key until the user decided about biometrics again. */
    fun wasInvalidated(): Boolean

    fun acknowledgeInvalidation()
}

internal fun isWalletPin(pin: String): Boolean = pin.length == 6 && pin.all { it in '0'..'9' }
