package jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

class FingerprintWrapper(
    private val biometricManager: BiometricManager,
    private val biometricPrompt: BiometricPrompt,
    private val promptInfo: BiometricPrompt.PromptInfo
) {

    private var isAuthActive = false

    fun startAuth() {
        biometricPrompt.authenticate(promptInfo)
        isAuthActive = true
    }

    fun cancel() {
        biometricPrompt.cancelAuthentication()
        isAuthActive = false
    }

    fun isAuthReady(): Boolean {
        return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun toggleScanner() {
        if (isAuthActive) {
            cancel()
        } else {
            startAuth()
        }
    }
}