package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The PIN screen during wrong-PIN delays: what it says, and that biometrics clear the count. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeRetryDelayViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val recovery = mockk<WalletPinRecovery>(relaxed = true)
    private val vibrator = mockk<DeviceVibrator>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { getString(R.string.wallet_emergency_locked, 30) } returns "Try again in 30 seconds."
    }
    private val stores = mutableListOf<ViewModelStore>()
    private val delayed = mockk<DelayedNavigation>()
    private val saved = SavedStateHandle(mapOf(PincodeFragment.KEY_PINCODE_ACTION to PinCodeAction.Check(delayed, ToolbarConfiguration())))

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        coEvery { interactor.isBiometricEnabled() } returns true
        coEvery { interactor.isPinCorrect(any()) } returns false
    }

    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        WalletViewOnlyAccess.pinResetCompleted()
        Dispatchers.resetMain()
    }

    private fun model(): PinCodeViewModel = PinCodeViewModel(interactor, router, vibrator, resources, saved, recovery).also {
        stores += ViewModelStore().apply { put("pin", it) }
        it.startAuth()
    }

    @Test fun wrongPinDuringADelaySaysHowLongPinEntryIsPaused() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 29_001L
        val model = model()
        runCurrent()
        model.pinCodeEntered("000000")
        runCurrent()
        assertEquals("Try again in 30 seconds.", model.pinRetryDelayEvent.value!!.peekContent())
        verify(exactly = 1) { vibrator.makeShortVibration() }
        verify(exactly = 0) { router.openAfterPinCode(any()) }
    }

    @Test fun wrongPinWithoutADelayShowsNoPause() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 0L
        val model = model()
        runCurrent()
        model.pinCodeEntered("000000")
        runCurrent()
        assertNull(model.pinRetryDelayEvent.value)
        verify(exactly = 1) { vibrator.makeShortVibration() }
    }

    @Test fun delayedPinEntriesReadNeitherPinHealthNorTheStoredPin() = runTest(dispatcher) {
        var delay = 29_001L
        coEvery { interactor.getPinRetryDelayMillis() } answers { delay }
        val model = model()
        runCurrent()
        // Initial setup checks PIN health to choose between entry and recovery.
        clearMocks(interactor, answers = false)

        repeat(2) {
            model.pinCodeEntered("123456")
            runCurrent()
        }

        coVerify(exactly = 0) { interactor.getPinHealth() }
        coVerify(exactly = 0) { interactor.isPinCorrect(any()) }
        coVerify(exactly = 2) { interactor.getPinRetryDelayMillis() }
        assertEquals("Try again in 30 seconds.", model.pinRetryDelayEvent.value!!.peekContent())
        verify(exactly = 2) { vibrator.makeShortVibration() }
        verify(exactly = 0) { router.openAfterPinCode(any()) }

        delay = 0L
        coEvery { interactor.isPinCorrect("123456") } returns true
        model.pinCodeEntered("123456")
        runCurrent()

        coVerify(exactly = 1) { interactor.getPinHealth() }
        coVerify(exactly = 1) { interactor.isPinCorrect("123456") }
        verify(exactly = 1) { router.openAfterPinCode(delayed) }
    }

    @Test fun wrongPinThatStartsADelayShowsTheNewWait() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returnsMany listOf(0L, 30_000L)
        val model = model()
        runCurrent()
        model.pinCodeEntered("000000")
        runCurrent()

        coVerify(exactly = 1) { interactor.isPinCorrect("000000") }
        assertEquals("Try again in 30 seconds.", model.pinRetryDelayEvent.value!!.peekContent())
        verify(exactly = 1) { vibrator.makeShortVibration() }
    }

    @Test fun biometricUnlockDuringADelayClearsTheCountBeforeOpeningTheWallet() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 300_000L
        val model = model()
        runCurrent()
        model.biometryAuthenticationSucceeded()
        runCurrent()
        coVerifyOrder {
            interactor.registerBiometricUnlock()
            router.openAfterPinCode(delayed)
        }
    }

    @Test fun biometricCallbackWithBiometricsTurnedOffClearsNothing() = runTest(dispatcher) {
        coEvery { interactor.isBiometricEnabled() } returns false
        val model = model()
        runCurrent()
        model.biometryAuthenticationSucceeded()
        runCurrent()
        coVerify(exactly = 0) { interactor.registerBiometricUnlock() }
        verify(exactly = 0) { router.openAfterPinCode(any()) }
    }
}
