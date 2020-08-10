package jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt

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