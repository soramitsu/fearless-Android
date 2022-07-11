package jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint

import androidx.biometric.BiometricPrompt
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PinCodeViewModel

class FingerprintCallback(
    private val pinCodeViewModel: PinCodeViewModel
) : BiometricPrompt.AuthenticationCallback() {

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (errMsgId != BiometricPrompt.ERROR_CANCELED &&
            errMsgId != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
            errMsgId != BiometricPrompt.ERROR_USER_CANCELED
        ) {
            pinCodeViewModel.onAuthenticationError(errString.toString())
        }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        pinCodeViewModel.biometryAuthenticationSucceeded()
    }

    override fun onAuthenticationFailed() {
        pinCodeViewModel.biometryAuthenticationFailed()
    }
}
