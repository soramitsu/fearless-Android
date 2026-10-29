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
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeLockViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val vibrator = mockk<DeviceVibrator>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { getString(R.string.wallet_emergency_locked, *anyVararg()) } answers {
            "locked-${secondArg<Array<Any>>().single()}"
        }
    }
    private val delayed = mockk<DelayedNavigation>()
    private val saved = SavedStateHandle(
        mapOf(PincodeFragment.KEY_PINCODE_ACTION to PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = true)))
    )
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        coEvery { interactor.isBiometricEnabled() } returns false
    }

    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    private fun model(): PinCodeViewModel =
        PinCodeViewModel(interactor, router, vibrator, resources, saved, mockk<WalletPinRecovery>(relaxed = true)).also {
            stores += ViewModelStore().apply { put("pin", it) }
            it.startAuth()
        }

    private fun lockedSeconds(model: PinCodeViewModel): Long =
        model.pinRetryDelayEvent.value!!.peekContent().removePrefix("locked-").toLong()

    @Test fun `a PIN entered during a lock is not checked and shows the remaining time`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 90_000L
        val model = model()
        runCurrent()

        model.pinCodeEntered("123456")
        runCurrent()

        coVerify(exactly = 0) { interactor.isPinCorrect(any()) }
        assertEquals(90L, lockedSeconds(model))
        assertNotNull(model.matchingPincodeErrorEvent.value)
        verify(exactly = 0) { router.openAfterPinCode(any()) }
    }

    @Test fun `the failure that starts a lock also shows how long it lasts`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returnsMany listOf(0L, 30_000L)
        coEvery { interactor.isPinCorrect("111111") } returns false
        val model = model()
        runCurrent()

        model.pinCodeEntered("111111")
        runCurrent()

        coVerify(exactly = 1) { interactor.isPinCorrect("111111") }
        assertEquals(30L, lockedSeconds(model))
        assertNotNull(model.matchingPincodeErrorEvent.value)
    }

    @Test fun `an ordinary wrong PIN outside a lock shows no lock message`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 0L
        coEvery { interactor.isPinCorrect("111111") } returns false
        val model = model()
        runCurrent()

        model.pinCodeEntered("111111")
        runCurrent()

        assertNull(model.pinRetryDelayEvent.value)
        assertNotNull(model.matchingPincodeErrorEvent.value)
    }

    @Test fun `the right PIN outside a lock authenticates`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 0L
        coEvery { interactor.isPinCorrect("123456") } returns true
        val model = model()
        runCurrent()

        model.pinCodeEntered("123456")
        runCurrent()

        assertNull(model.pinRetryDelayEvent.value)
        verify(exactly = 1) { router.openAfterPinCode(delayed) }
    }

    @Test fun `a lock about to end still reads as at least one second`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 300L
        val model = model()
        runCurrent()

        model.pinCodeEntered("123456")
        runCurrent()

        assertEquals(1L, lockedSeconds(model))
        coVerify(exactly = 0) { interactor.isPinCorrect(any()) }
    }

    @Test fun `a lock end already in the past lets the PIN be checked`() = runTest(dispatcher) {
        coEvery { interactor.getPinRetryDelayMillis() } returns 0L
        coEvery { interactor.isPinCorrect("123456") } returns true
        val model = model()
        runCurrent()

        model.pinCodeEntered("123456")
        runCurrent()

        assertNull(model.pinRetryDelayEvent.value)
        verify(exactly = 1) { router.openAfterPinCode(delayed) }
    }
}
