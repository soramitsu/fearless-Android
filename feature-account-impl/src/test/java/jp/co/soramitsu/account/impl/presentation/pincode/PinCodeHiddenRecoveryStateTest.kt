package jp.co.soramitsu.account.impl.presentation.pincode

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault
import jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The recovery panel sits hidden (GONE) under the PIN pad. Its hidden state said loading, and the panel draws an
 * indeterminate spinner while loading, so an invisible spinner kept asking for animation frames for as long as the
 * PIN pad was open. Hidden states are not loading; the spinner shows only while the panel is visible and loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinCodeHiddenRecoveryStateTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val recovery = mockk<WalletPinRecovery>(relaxed = true)
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val delayed = mockk<DelayedNavigation>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.isBiometricEnabled() } returns false
    }

    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test fun `the panel is hidden and idle while the PIN health loads`() = runTest(dispatcher) {
        val model = model(PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = false)))

        assertHiddenAndIdle(model)
    }

    @Test fun `unlocking with a readable PIN keeps the panel hidden and idle`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.HEALTHY
        val model = model(PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = false)))
        model.startAuth()
        runCurrent()

        assertHiddenAndIdle(model)
    }

    @Test fun `creating the first PIN keeps the panel hidden and idle`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.ABSENT
        val model = model(PinCodeAction.Create(delayed))
        model.startAuth()
        runCurrent()

        assertHiddenAndIdle(model)
    }

    @Test fun `an unreadable PIN shows the panel loading until its choices arrive`() = runTest(dispatcher) {
        coEvery { interactor.getPinHealth() } returns PinHealth.UNREADABLE
        coEvery { recovery.begin() } returns "challenge"
        val model = model(PinCodeAction.Check(delayed, ToolbarConfiguration(backVisible = false)))
        model.startAuth()
        dispatcher.scheduler.runCurrent()

        val state = model.recoveryState.value!!
        assertTrue(state.visible)
        assertFalse(state.loading)
    }

    private fun assertHiddenAndIdle(model: PinCodeViewModel) {
        val state = model.recoveryState.value!!
        assertFalse(state.visible)
        assertFalse("a hidden panel must not run its loading spinner", state.loading)
    }

    private fun model(action: PinCodeAction): PinCodeViewModel {
        val saved = SavedStateHandle(mapOf(PincodeFragment.KEY_PINCODE_ACTION to action))
        return PinCodeViewModel(interactor, mockk<AccountRouter>(relaxed = true), mockk<DeviceVibrator>(relaxed = true), resources, saved,
            recovery, mockk<BiometricPinVault>(relaxed = true)).also {
            it.vaultDispatcher = dispatcher
            stores += ViewModelStore().apply { put("pin", it) }
        }
    }
}
