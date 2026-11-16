package jp.co.soramitsu.account.impl.presentation.account.rename

import androidx.lifecycle.SavedStateHandle
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenameAccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val interactor = mockk<AccountInteractor>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RenameAccountViewModel(
        router, interactor, VmTestFixtures.resourceManager(),
        SavedStateHandle(mapOf(RenameAccountDialog.WALLET_ID_KEY to 5L, RenameAccountDialog.WALLET_NAME_KEY to "Old name"))
    )

    @Test
    fun `the field starts with the current name and save follows whether the name is blank`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("Old name", vm.state.value.walletNickname?.text)
        runCurrent()
        assertTrue(vm.state.value.isSaveEnabled)
        assertEquals("s${R.string.wallet_name}", vm.state.value.walletNickname?.hint)

        vm.accountNameChanged("   "); runCurrent()
        assertFalse(vm.state.value.isSaveEnabled)

        vm.accountNameChanged("New name"); runCurrent()
        assertEquals("New name", vm.state.value.walletNickname?.text)
        assertTrue(vm.state.value.isSaveEnabled)
    }

    @Test
    fun `save stores the typed name for this wallet before closing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.accountNameChanged("Travel")
        vm.onSaveClicked()
        runCurrent()

        coVerifyOrder {
            interactor.updateAccountName(5L, "Travel")
            router.back()
        }
    }

    @Test
    fun `back closes without renaming and a missing wallet id is refused`() = runTest(dispatcher) {
        viewModel().onBackClick()
        runCurrent()

        verify { router.back() }
        coVerify(exactly = 0) { interactor.updateAccountName(any(), any()) }
        assertThrows(IllegalStateException::class.java) {
            RenameAccountViewModel(router, interactor, VmTestFixtures.resourceManager(), SavedStateHandle())
        }
    }
}
