package jp.co.soramitsu.account.impl.presentation.pincode.fingerprint

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import javax.crypto.Cipher

/** Runs the unlock prompt with the biometric PIN vault's cipher as its CryptoObject. */
class FingerprintWrapper(
    private val biometricManager: BiometricManager,
    private val biometricPrompt: BiometricPrompt,
    private val promptInfo: BiometricPrompt.PromptInfo
) {

    fun startAuth(cipher: Cipher) {
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun cancel() {
        biometricPrompt.cancelAuthentication()
    }

    /** Only Class 3 (strong) biometrics can use the vault's Keystore key. */
    fun isAuthReady(): Boolean {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
