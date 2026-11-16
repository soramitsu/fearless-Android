package jp.co.soramitsu.account.impl.presentation.pincode

import android.app.KeyguardManager
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.testutil.closeFragmentHost
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentPincodeBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class PincodeFragmentTest {

    private val vm = mockk<PinCodeViewModel>(relaxed = true)
    private val gate = PinBiometricPromptGate()
    private val startScanner = MutableLiveData<Event<Unit>>()
    private val biometricSwitch = MutableLiveData<Event<Boolean>>()
    private val showFingerprint = MutableLiveData<Event<Boolean>>()
    private val fingerprintError = MutableLiveData<Event<String>>()
    private val homeVisible = MutableLiveData(true)
    private val forgotVisible = MutableLiveData(false)
    private val matchingError = MutableLiveData<Event<Unit>>()
    private val retryDelay = MutableLiveData<Event<String>>()
    private val resetInput = MutableLiveData<Event<String>>()
    // The view model's hidden recovery state is idle (coverage bug #28): no spinner runs under the PIN pad.
    private val hidden = PinCodeViewModel.RecoveryState.HIDDEN
    private val recovery = MutableLiveData(hidden)

    private lateinit var fragment: PincodeFragment
    private lateinit var binding: FragmentPincodeBinding

    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    private val context: Context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private fun host(action: PinCodeAction = PinCodeAction.Change, biometricsReady: Boolean = false) = run {
        // Robolectric reports strong biometrics as available unless told otherwise.
        Shadow.extract<ShadowBiometricManager>(
            context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
        ).setCanAuthenticate(biometricsReady)
        every { vm.pinCodeAction } returns action
        every { vm.biometricPromptGate } returns gate
        every { vm.startFingerprintScannerEventLiveData } returns startScanner
        every { vm.biometricSwitchDialogLiveData } returns biometricSwitch
        every { vm.showFingerPrintEvent } returns showFingerprint
        every { vm.fingerPrintErrorEvent } returns fingerprintError
        every { vm.homeButtonVisibilityLiveData } returns homeVisible
        every { vm.forgotPinVisible } returns forgotVisible
        every { vm.matchingPincodeErrorEvent } returns matchingError
        every { vm.pinRetryDelayEvent } returns retryDelay
        every { vm.resetInputEvent } returns resetInput
        every { vm.recoveryState } returns recovery
        fragment = PincodeFragment()
        val controller = hostFragment(fragment, vm)
        binding = FragmentPincodeBinding.bind(fragment.requireView())
        controller
    }

    private fun tap(vararg digits: Int) = digits.forEach { digit ->
        binding.pinCodeView.findViewById<View>(DIGIT_IDS[digit]).performClick()
    }

    @Test
    fun keypadForgotPinBackAndToolbarReachTheViewModelOnASecureWindow() {
        val controller = host()

        val title = binding.toolbar.findViewById<TextView>(jp.co.soramitsu.common.R.id.titleTv).text.toString()
        assertEquals(fragment.getString(R.string.profile_pincode_change_title), title)
        assertTrue(controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        verify { vm.fingerprintScannerAvailable(false) }
        verify { vm.configureRecoveryAuthentication(false, false) }
        verify { vm.startAuth() }

        tap(1, 2, 3, 4, 5, 6)
        binding.forgotPin.performClick()
        controller.get().onBackPressedDispatcher.onBackPressed()
        binding.toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        verify { vm.pinCodeEntered("123456") }
        verify { vm.forgotPin() }
        verify(exactly = 2) { vm.backPressed() }
        controller.closeFragmentHost()
    }

    @Test
    fun deviceSecurityIsReportedForRecoveryChoices() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)

        host(PinCodeAction.Check(null, ToolbarConfiguration(null, false)))

        verify { vm.configureRecoveryAuthentication(false, true) }
        verify { vm.startAuth() }
    }

    @Test
    fun strongBiometricsShowTheFingerprintKeyWhichAsksForTheVaultCipher() {
        host(biometricsReady = true)
        verify { vm.fingerprintScannerAvailable(true) }
        verify { vm.configureRecoveryAuthentication(true, false) }
        every { vm.biometricUnlockCipher() } returns null

        showFingerprint.value = Event(true)
        val key = binding.pinCodeView.findViewById<View>(R.id.fingerprintBtn)
        assertEquals(View.VISIBLE, key.visibility)
        key.performClick()
        startScanner.value = Event(Unit)
        verify(exactly = 2) { vm.biometricUnlockCipher() }
        assertNull(gate.inFlight())

        recovery.value = PinCodeViewModel.RecoveryState(visible = true, loading = false)
        startScanner.value = Event(Unit)
        verify(exactly = 2) { vm.biometricUnlockCipher() }

        showFingerprint.value = Event(false)
        assertEquals(View.INVISIBLE, key.visibility)
    }

    @Test
    fun visibilityStatesSwapTheKeypadForRecoveryAndClearPartialInput() {
        host()

        forgotVisible.value = true
        assertEquals(View.VISIBLE, binding.forgotPin.visibility)
        homeVisible.value = false
        assertEquals(View.GONE, binding.toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).visibility)

        tap(9, 9, 9)
        recovery.value = PinCodeViewModel.RecoveryState(visible = true, loading = false)
        assertEquals(View.VISIBLE, binding.pinRecoveryContent.visibility)
        assertEquals(View.GONE, binding.pinCodeView.visibility)

        recovery.value = hidden
        assertEquals(View.GONE, binding.pinRecoveryContent.visibility)
        assertEquals(View.VISIBLE, binding.pinCodeView.visibility)
        tap(1, 2, 3, 4, 5, 6)
        verify { vm.pinCodeEntered("123456") }
        verify(exactly = 0) { vm.pinCodeEntered(match { it.startsWith("999") }) }
    }

    @Test
    fun resetEventRetitlesTheKeypadHidesTheFingerprintAndDropsTypedDigits() {
        host()
        tap(1, 2, 3)

        resetInput.value = Event("Repeat your PIN")
        tap(4, 5, 6, 7, 8, 9)

        val pinTitle = binding.pinCodeView.findViewById<TextView>(R.id.pinCodeTitleTv)
        assertEquals("Repeat your PIN", pinTitle.text.toString())
        assertEquals(View.INVISIBLE, binding.pinCodeView.findViewById<View>(R.id.fingerprintBtn).visibility)
        verify { vm.pinCodeEntered("456789") }
    }

    @Test
    fun retryDelaysAndFingerprintErrorsAreToasted() {
        host()

        retryDelay.value = Event("Try again in 30 s")
        assertEquals("Try again in 30 s", ShadowToast.getTextOfLatestToast())
        fingerprintError.value = Event("Sensor unavailable")
        assertEquals("Sensor unavailable", ShadowToast.getTextOfLatestToast())

        showFingerprint.value = Event(true)
        assertEquals(View.INVISIBLE, binding.pinCodeView.findViewById<View>(R.id.fingerprintBtn).visibility)
        matchingError.value = Event(Unit)
        tap(2, 4, 6, 8, 0, 2)
        verify { vm.pinCodeEntered("246802") }
    }

    @Test
    fun biometricOfferIsAnUnhideableDialogNamingTheBiometricChange() {
        host()

        biometricSwitch.value = Event(true)
        fragment.childFragmentManager.executePendingTransactions()

        val dialog = fragment.childFragmentManager.fragments.filterIsInstance<ErrorDialog>().single()
        val arguments = dialog.requireArguments()
        assertEquals(fragment.getString(R.string.pincode_biometrics_changed_offer), arguments.getString("error_dialog_message"))
        assertEquals(fragment.getString(R.string.common_use), arguments.getString("error_dialog_positive_button_text"))
        assertFalse(arguments.getBoolean("error_dialog_is_hideable"))
    }

    @Test
    fun resumeAndStopAreForwardedAndTheScannerStaysOffWithoutBiometrics() {
        val controller = host()
        verify { vm.onResume() }

        startScanner.value = Event(Unit)
        verify(exactly = 0) { vm.biometricUnlockCipher() }

        // The device-credential screen pauses this one; that prompt must survive the pause.
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL))
        controller.pause()
        assertEquals(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL, gate.finish())
        controller.resume()
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        controller.pause()
        assertNull(gate.finish())

        controller.stop()
        verify { vm.screenStopped() }
        controller.closeFragmentHost()
    }

    @Test
    fun authenticationResultsAreRoutedByThePromptThatWasOpened() {
        val controller = host()
        val callback = clientCallback(controller.get())

        assertTrue(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC))
        verify { vm.biometryAuthenticationSucceeded(null) }

        assertTrue(gate.begin(PinBiometricPromptGate.Kind.STRONG_RECOVERY))
        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL))
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.STRONG_RECOVERY))
        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC))
        verify(exactly = 1) { vm.strongBiometricSucceeded(null) }

        assertTrue(gate.begin(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL))
        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC))
        assertTrue(gate.begin(PinBiometricPromptGate.Kind.DEVICE_CREDENTIAL))
        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL))
        verify(exactly = 1) { vm.deviceCredentialSucceeded() }

        callback.onAuthenticationSucceeded(result(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC))
        verify(exactly = 1) { vm.biometryAuthenticationSucceeded(any()) }
        assertNull(gate.inFlight())
        controller.closeFragmentHost()
    }

    @Test
    fun onlyRealAuthenticationErrorsAreReportedAndFailuresAreCounted() {
        val controller = host()
        val callback = clientCallback(controller.get())

        assertTrue(gate.begin(PinBiometricPromptGate.Kind.NORMAL))
        callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled")
        callback.onAuthenticationError(BiometricPrompt.ERROR_CANCELED, "cancelled")
        callback.onAuthenticationError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "cancelled")
        callback.onAuthenticationError(BiometricPrompt.ERROR_LOCKOUT, "Too many attempts")
        callback.onAuthenticationFailed()

        assertNull(gate.inFlight())
        verify(exactly = 1) { vm.onAuthenticationError(any()) }
        verify { vm.onAuthenticationError("Too many attempts") }
        verify { vm.biometryAuthenticationFailed() }
        controller.closeFragmentHost()
    }

    private fun clientCallback(activity: androidx.fragment.app.FragmentActivity): BiometricPrompt.AuthenticationCallback {
        val model = ViewModelProvider(activity)[BiometricViewModel::class.java]
        return BiometricViewModel::class.java.getDeclaredField("mClientCallback")
            .apply { isAccessible = true }
            .get(model) as BiometricPrompt.AuthenticationCallback
    }

    private fun result(type: Int) = mockk<BiometricPrompt.AuthenticationResult> {
        every { authenticationType } returns type
        every { cryptoObject } returns null
    }

    private companion object {
        val DIGIT_IDS = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
    }
}
