package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.crypto.Cipher
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeBiometricVaultViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val recovery = mockk<WalletPinRecovery>(relaxed = true)
    private val vibrator = mockk<DeviceVibrator>(relaxed = true)
    private val vault = mockk<BiometricPinVault>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val delayed = mockk<DelayedNavigation>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        WalletSessionLock.lock()
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        coEvery { interactor.isBiometricEnabled() } returns true
        coEvery { interactor.getPinRetryDelayMillis() } returns 0L
        coEvery { interactor.isPinCorrect("123456") } returns true
        every { vault.isEnrolled() } returns true
        every { vault.wasInvalidated() } returns false
        every { vault.prepareUnlock() } returns BiometricPinVault.UnlockCipher.Ready(cipher)
    }

    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        WalletViewOnlyAccess.pinResetCompleted()
        WalletSessionLock.lock()
        Dispatchers.resetMain()
    }

    private fun model(action: PinCodeAction): PinCodeViewModel {
        val saved = SavedStateHandle(mapOf(PincodeFragment.KEY_PINCODE_ACTION to action))
        return PinCodeViewModel(interactor, router, vibrator, resources, saved, recovery, vault).also {
            it.vaultDispatcher = dispatcher
            it.fingerprintScannerAvailable(true)
            stores += ViewModelStore().apply { put("pin", it) }
            it.startAuth()
        }
    }

    private fun unlock() = PinCodeAction.Check(null, ToolbarConfiguration())
    private fun export() = PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = true), allowBiometric = false)

    @Test fun `unlock offers the sealed biometric and opens the wallet with the vault PIN`() = runTest(dispatcher) {
        coEvery { interactor.verifyBiometricVaultPin("123456") } returns true
        every { vault.decryptPin(cipher) } returns "123456"
        val model = model(unlock())
        runCurrent()
        assertTrue(model.showFingerPrintEvent.value!!.peekContent())
        assertEquals(cipher, model.biometricUnlockCipher())
        model.biometryAuthenticationSucceeded(cipher)
        runCurrent()
        verify(exactly = 1) { router.back() }
        assertFalse(WalletSessionLock.isLocked())
    }

    @Test fun `exports never accept a biometric, even a valid sealed one`() = runTest(dispatcher) {
        coEvery { interactor.verifyBiometricVaultPin("123456") } returns true
        every { vault.decryptPin(cipher) } returns "123456"
        val model = model(export())
        runCurrent()
        assertFalse(model.showFingerPrintEvent.value!!.peekContent())
        assertNull(model.biometricUnlockCipher())
        model.biometryAuthenticationSucceeded(cipher)
        runCurrent()
        coVerify(exactly = 0) { interactor.verifyBiometricVaultPin(any()) }
        verify(exactly = 0) { router.openAfterPinCode(any()) }
        verify(exactly = 0) { vault.prepareUnlock() }
        assertNull(model.startFingerprintScannerEventLiveData.value)
    }

    @Test fun `exports still open with the typed PIN`() = runTest(dispatcher) {
        val model = model(export())
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        verify(exactly = 1) { router.openAfterPinCode(delayed) }
    }

    @Test fun `changing the PIN takes the old PIN, never a biometric`() = runTest(dispatcher) {
        every { vault.decryptPin(cipher) } returns "123456"
        val model = model(PinCodeAction.Change)
        runCurrent()
        assertFalse(model.showFingerPrintEvent.value!!.peekContent())
        model.biometryAuthenticationSucceeded(cipher)
        runCurrent()
        coVerify(exactly = 0) { interactor.verifyBiometricVaultPin(any()) }
        coVerify(exactly = 0) { interactor.savePin(any()) }
    }

    @Test fun `a changed PIN is resealed while biometric unlock stays on`() = runTest(dispatcher) {
        val model = model(PinCodeAction.Change)
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        model.pinCodeEntered("654321")
        model.pinCodeEntered("654321")
        runCurrent()
        coVerifyOrder {
            interactor.savePin("654321")
            vault.enroll("654321")
        }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `a changed PIN clears the vault when biometric unlock is off`() = runTest(dispatcher) {
        coEvery { interactor.isBiometricEnabled() } returns false
        val model = model(PinCodeAction.Change)
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        model.pinCodeEntered("654321")
        model.pinCodeEntered("654321")
        runCurrent()
        verify(exactly = 1) { vault.clear() }
        verify(exactly = 0) { vault.enroll(any()) }
    }

    @Test fun `a biometric enrolled after sealing turns biometric unlock off and asks after the PIN`() = runTest(dispatcher) {
        every { vault.prepareUnlock() } returns BiometricPinVault.UnlockCipher.Invalidated
        val model = model(unlock())
        runCurrent()
        assertNull(model.biometricUnlockCipher())
        runCurrent()
        coVerify(exactly = 1) { interactor.setBiometricOff() }
        assertFalse(model.showFingerPrintEvent.value!!.peekContent())

        every { vault.wasInvalidated() } returns true
        model.pinCodeEntered("123456")
        runCurrent()
        assertTrue("The offer explains the biometric change", model.biometricSwitchDialogLiveData.value!!.peekContent())
        verify(exactly = 0) { router.back() }

        model.acceptAuthWithBiometry()
        runCurrent()
        coVerifyOrder {
            vault.acknowledgeInvalidation()
            vault.enroll("123456")
            interactor.setBiometricOn()
            router.back()
        }
    }

    @Test fun `declining after a biometric change keeps biometric unlock off`() = runTest(dispatcher) {
        every { vault.wasInvalidated() } returns true
        val model = model(unlock())
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        model.declineAuthWithBiometry()
        runCurrent()
        verify(exactly = 1) { vault.acknowledgeInvalidation() }
        verify(exactly = 1) { vault.clear() }
        verify(exactly = 0) { vault.enroll(any()) }
        coVerify(exactly = 1) { interactor.setBiometricOff() }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `a wallet upgraded with unsealed biometrics is sealed on the next PIN unlock`() = runTest(dispatcher) {
        every { vault.isEnrolled() } returns false
        val model = model(unlock())
        runCurrent()
        assertFalse(model.showFingerPrintEvent.value!!.peekContent())
        model.pinCodeEntered("123456")
        runCurrent()
        coVerifyOrder {
            vault.enroll("123456")
            router.back()
        }
    }

    @Test fun `a failed seal still unlocks with the PIN`() = runTest(dispatcher) {
        every { vault.isEnrolled() } returns false
        every { vault.enroll(any()) } throws IllegalStateException("keystore busy")
        val model = model(unlock())
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        verify(exactly = 1) { vault.clear() }
        verify(exactly = 1) { router.back() }
    }

    @Test fun `onboarding seals the new PIN when biometrics are accepted`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.ABSENT
        every { vault.isEnrolled() } returns false
        val model = model(PinCodeAction.Create(delayed))
        runCurrent()
        model.pinCodeEntered("246810")
        model.pinCodeEntered("246810")
        runCurrent()
        assertFalse(model.biometricSwitchDialogLiveData.value!!.peekContent())
        model.acceptAuthWithBiometry()
        runCurrent()
        coVerifyOrder {
            interactor.savePin("246810")
            vault.enroll("246810")
            interactor.setBiometricOn()
            router.openAfterPinCode(delayed)
        }
        assertFalse(WalletSessionLock.isLocked())
    }

    @Test fun `a stopped screen forgets the PIN it would have sealed`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.ABSENT
        val model = model(PinCodeAction.Create(delayed))
        runCurrent()
        model.pinCodeEntered("246810")
        model.pinCodeEntered("246810")
        runCurrent()
        model.screenStopped()
        model.acceptAuthWithBiometry()
        runCurrent()
        verify(exactly = 0) { vault.enroll(any()) }
        coVerify(exactly = 1) { interactor.setBiometricOn() }
    }

    @Test fun `strong recovery with a sealed vault needs the vault cipher`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.UNREADABLE
        coEvery { recovery.begin() } returns "challenge"
        coEvery { recovery.choices(any()) } returns emptyList()
        every { recovery.isActive(any()) } returns true
        val model = model(unlock())
        model.configureRecoveryAuthentication(strong = true, deviceCredential = false)
        runCurrent()
        assertTrue(model.recoveryBiometricPrompt() is PinCodeViewModel.RecoveryPrompt.Sealed)

        model.strongBiometricSucceeded(null)
        runCurrent()
        coVerify(exactly = 0) { recovery.strongBiometricSucceeded(any(), any()) }

        every { vault.decryptPin(cipher) } returns "123456"
        model.strongBiometricSucceeded(cipher)
        runCurrent()
        coVerify(exactly = 1) { recovery.strongBiometricSucceeded("challenge", any()) }
    }

    @Test fun `strong recovery is blocked after the phone's biometrics changed`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.UNREADABLE
        coEvery { recovery.begin() } returns "challenge"
        coEvery { recovery.choices(any()) } returns emptyList()
        every { recovery.isActive(any()) } returns true
        every { vault.isEnrolled() } returns false
        every { vault.wasInvalidated() } returns true
        val model = model(unlock())
        model.configureRecoveryAuthentication(strong = true, deviceCredential = false)
        runCurrent()
        assertEquals(PinCodeViewModel.RecoveryPrompt.Blocked, model.recoveryBiometricPrompt())
        model.strongBiometricSucceeded(null)
        runCurrent()
        coVerify(exactly = 0) { recovery.strongBiometricSucceeded(any(), any()) }
    }
}
