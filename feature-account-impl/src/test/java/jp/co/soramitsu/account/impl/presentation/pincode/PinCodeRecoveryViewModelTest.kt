package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeRecoveryViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val recovery = mockk<WalletPinRecovery>(relaxed = true)
    private val vibrator = mockk<DeviceVibrator>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val stores = mutableListOf<ViewModelStore>()
    private val target = RecoveryTarget(7, "ethereum")
    private val delayed = mockk<DelayedNavigation>()
    private val saved = SavedStateHandle(mapOf(PincodeFragment.KEY_PINCODE_ACTION to PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = true))))

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.getPinHealth() } returns PinHealth.UNREADABLE
        coEvery { interactor.isBiometricEnabled() } returns true
        coEvery { recovery.begin() } returns "in-memory-challenge"
        coEvery { recovery.choices(any()) } returns listOf(WalletPinRecovery.Choice("Wallet", target))
        every { recovery.isAuthorized(any()) } returns false
        every { recovery.isActive(any()) } returns true
    }
    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        WalletViewOnlyAccess.pinResetCompleted()
        Dispatchers.resetMain()
    }

    private fun model(): PinCodeViewModel = PinCodeViewModel(interactor, router, vibrator, resources, saved, recovery).also {
        stores += ViewModelStore().apply { put("pin", it) }
        it.configureRecoveryAuthentication(strong = true, deviceCredential = true)
        it.startAuth()
    }

    @Test fun unreadablePinCannotTakeOnboardingOrOrdinaryBiometricShortcuts() = runTest(dispatcher) {
        saved[PincodeFragment.KEY_PINCODE_ACTION] = PinCodeAction.Create(delayed)
        val model = model()
        model.pinCodeEntered("123456") // Health has not loaded yet.
        model.biometryAuthenticationSucceeded()
        runCurrent()
        assertTrue(model.recoveryState.value!!.visible)
        assertFalse(model.recoveryState.value!!.loading)
        assertTrue(model.recoveryState.value!!.strongBiometric)
        model.pinCodeEntered("123456")
        model.biometryAuthenticationSucceeded()
        runCurrent()
        coVerify(exactly = 0) { interactor.savePin(any()); interactor.isPinCorrect(any()) }
        verify(exactly = 0) { router.openAfterPinCode(any()) }
        assertEquals(setOf(PincodeFragment.KEY_PINCODE_ACTION), saved.keys())
    }

    @Test fun strongProofRequiresTwoMatchingNewPinEntriesAndUsesSnapshotBoundService() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.strongBiometricSucceeded()
        runCurrent()
        assertFalse(model.recoveryState.value!!.visible)
        model.pinCodeEntered("123456")
        model.pinCodeEntered("654321")
        verify(exactly = 1) { vibrator.makeShortVibration() }
        coVerify(exactly = 0) { recovery.saveNewPin(any(), any()) }
        model.pinCodeEntered("123456")
        runCurrent()
        coVerify(exactly = 1) { recovery.strongBiometricSucceeded("in-memory-challenge") }
        coVerify(exactly = 1) { recovery.saveNewPin("in-memory-challenge", "123456") }
        coVerify(exactly = 0) { interactor.savePin(any()) }
        verify(exactly = 1) { router.openAfterPinCode(delayed) }
    }

    @Test fun deviceUnlockOpensOnlyMainAndDiscardsPendingPrivateAction() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.deviceCredentialSucceeded()
        assertTrue(WalletViewOnlyAccess.isEnabled())
        verify(exactly = 1) { router.openMain(); recovery.cancel("in-memory-challenge") }
        verify(exactly = 0) { router.openAfterPinCode(any()); router.back() }
        coVerify(exactly = 0) { recovery.saveNewPin(any(), any()); recovery.strongBiometricSucceeded(any()) }
    }

    @Test fun unavailableOrPreviouslyDisabledBiometricsCannotAuthorizeRecovery() = runTest(dispatcher) {
        coEvery { interactor.isBiometricEnabled() } returns false
        val model = model()
        runCurrent()
        model.strongBiometricSucceeded()
        runCurrent()
        assertFalse(model.recoveryState.value!!.strongBiometric)
        coVerify(exactly = 0) { recovery.strongBiometricSucceeded(any()) }
        model.configureRecoveryAuthentication(strong = false, deviceCredential = false)
        model.deviceCredentialSucceeded()
        verify(exactly = 0) { router.openMain() }
    }

    @Test fun proofImportCarriesOnlyAnEligiblePublicTargetAndChallenge() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.verifyBackup(RecoveryTarget(8, "ethereum"), ImportMode.RawSeed)
        verify(exactly = 0) { router.openPinRecoveryImport(any(), any(), any()) }
        model.verifyBackup(target, ImportMode.RawSeed)
        verify { router.openPinRecoveryImport(target, ImportMode.RawSeed, "in-memory-challenge") }
        every { recovery.isAuthorized("in-memory-challenge") } returns true
        model.onResume()
        runCurrent()
        assertFalse(model.recoveryState.value!!.visible)
        assertEquals(setOf(PincodeFragment.KEY_PINCODE_ACTION), saved.keys())
    }

    @Test fun expiredOrChangedProofCannotFallBackToOrdinaryPinCreation() = runTest(dispatcher) {
        coEvery { recovery.saveNewPin(any(), any()) } throws IllegalStateException("expired")
        val model = model()
        runCurrent()
        model.strongBiometricSucceeded()
        runCurrent()
        model.pinCodeEntered("123456")
        model.pinCodeEntered("123456")
        runCurrent()
        assertTrue(model.recoveryState.value!!.error)
        verify { recovery.cancel("in-memory-challenge") }
        coVerify(exactly = 0) { interactor.savePin(any()) }
        verify(exactly = 0) { router.openAfterPinCode(any()) }
        model.retryRecovery()
        runCurrent()
        assertFalse(model.recoveryState.value!!.error)
        coVerify(exactly = 2) { recovery.begin() }
    }

    @Test fun stoppingClearsTemporaryPinAndBackCancelsTheGrant() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.strongBiometricSucceeded()
        runCurrent()
        model.pinCodeEntered("123456")
        model.screenStopped()
        model.pinCodeEntered("123456")
        runCurrent()
        coVerify(exactly = 0) { recovery.saveNewPin(any(), any()) }
        model.backPressed() // Confirmation returns to creation without committing.
        model.backPressed()
        verify { recovery.cancel("in-memory-challenge"); router.back() }
    }

    @Test fun viewModelDestructionRevokesTheInMemoryGrant() = runTest(dispatcher) {
        model()
        runCurrent()
        stores.forEach(ViewModelStore::clear)
        verify(exactly = 1) { recovery.cancel("in-memory-challenge") }
    }

    @Test fun genuineOnboardingStillCreatesAndOffersOptionalBiometrics() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.ABSENT
        saved[PincodeFragment.KEY_PINCODE_ACTION] = PinCodeAction.Create(delayed)
        val model = model()
        model.fingerprintScannerAvailable(true)
        runCurrent()
        model.pinCodeEntered("123456")
        model.pinCodeEntered("123456")
        runCurrent()
        coVerify { interactor.savePin("123456") }
        assertNotNull(model.biometricSwitchDialogLiveData.value)
        model.acceptAuthWithBiometry()
        runCurrent()
        coVerify { interactor.setBiometricOn() }
        verify { router.openAfterPinCode(delayed) }
    }

    @Test fun healthyPinRejectsInvalidInputAndKeepsNormalUnlockAndChangeFlow() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        coEvery { interactor.isPinCorrect("123456") } returns true
        saved[PincodeFragment.KEY_PINCODE_ACTION] = PinCodeAction.Change
        val model = model()
        runCurrent()
        model.pinCodeEntered("")
        model.pinCodeEntered("１２３４５６")
        runCurrent()
        coVerify(exactly = 0) { interactor.isPinCorrect(any()) }
        model.pinCodeEntered("123456")
        runCurrent()
        model.pinCodeEntered("654321")
        model.pinCodeEntered("654321")
        runCurrent()
        coVerify { interactor.savePin("654321") }
        verify { router.back() }
        coVerify(exactly = 0) { recovery.begin() }
    }

    @Test fun aPinThatBecomesUnreadableCannotBeBypassedByANormalBiometricCallback() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        val model = model()
        runCurrent()
        coEvery { interactor.getPinHealth() } returns PinHealth.UNREADABLE
        model.biometryAuthenticationSucceeded()
        runCurrent()
        assertTrue(model.recoveryState.value!!.visible)
        verify(exactly = 0) { router.openAfterPinCode(any()) }
    }

    @Test fun expiredProofOnReturnOrNewBackupAttemptOffersRetry() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        every { recovery.isActive(any()) } returns false
        model.verifyBackup(target, ImportMode.RawSeed)
        assertTrue(model.recoveryState.value!!.error)
        verify(exactly = 0) { router.openPinRecoveryImport(any(), any(), any()) }
        every { recovery.isActive(any()) } returns true
        model.retryRecovery()
        runCurrent()
        every { recovery.isActive(any()) } returns false
        model.onResume()
        runCurrent()
        assertTrue(model.recoveryState.value!!.error)
    }

    @Test fun readablePinAuthenticationEndsAnEarlierViewOnlySession() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        coEvery { interactor.isPinCorrect("123456") } returns true
        WalletViewOnlyAccess.enter()
        val model = model()
        runCurrent()
        model.pinCodeEntered("123456")
        runCurrent()
        assertFalse(WalletViewOnlyAccess.isEnabled())
        verify { router.openAfterPinCode(delayed) }
    }

    @Test fun pinConfirmationDiagnosticNeverContainsThePin() {
        assertEquals("Confirmation", PinCodeViewModel.ScreenState.Confirmation("123456").toString())
    }

    @Test fun genericAuthenticationMessagesAndDeclinePreserveExistingBehavior() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.ABSENT
        saved[PincodeFragment.KEY_PINCODE_ACTION] = PinCodeAction.Create(delayed)
        val model = model()
        runCurrent()
        model.onAuthenticationError("platform error")
        assertEquals("platform error", model.fingerPrintErrorEvent.value!!.peekContent())
        model.biometryAuthenticationFailed()
        assertNotEquals("platform error", model.fingerPrintErrorEvent.value!!.peekContent())
        model.pinCodeEntered("123456")
        model.pinCodeEntered("123456")
        runCurrent()
        model.declineAuthWithBiometry()
        runCurrent()
        coVerify { interactor.setBiometricOff() }
    }
}
