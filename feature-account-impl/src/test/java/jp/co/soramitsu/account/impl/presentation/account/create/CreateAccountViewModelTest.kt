package jp.co.soramitsu.account.impl.presentation.account.create

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.model.WalletEcosystem
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAccountViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(type: AccountType, fromGoogle: Boolean? = null) = CreateAccountViewModel(
        router, VmTestFixtures.resourceManager(),
        SavedStateHandle(
            buildMap<String, Any> {
                put(CreateAccountScreenKeys.ACCOUNT_TYPE_KEY, type)
                fromGoogle?.let { put(CreateAccountScreenKeys.IS_FROM_GOOGLE_BACKUP_KEY, it) }
            }
        )
    )

    @Test
    fun `continue follows a non-blank wallet name`() = runTest(dispatcher) {
        val vm = viewModel(AccountType.SubstrateOrEvm)
        runCurrent()
        assertFalse(vm.state.value.isContinueEnabled)
        assertEquals("s${R.string.wallet_name}", vm.state.value.walletNickname.hint)

        vm.accountNameChanged("Daily"); runCurrent()
        assertTrue(vm.state.value.isContinueEnabled)
        assertEquals("Daily", vm.state.value.walletNickname.text)
        assertEquals(true, vm.nextButtonEnabledLiveData.value)

        vm.accountNameChanged(" "); runCurrent()
        assertFalse(vm.state.value.isContinueEnabled)
        assertEquals(true, vm.nextButtonEnabledLiveData.value)
        vm.accountNameChanged(""); runCurrent()
        assertEquals(false, vm.nextButtonEnabledLiveData.value)
    }

    @Test
    fun `a new wallet first warns about screenshots then opens the phrase for its ecosystems`() = runTest(dispatcher) {
        val substrate = viewModel(AccountType.SubstrateOrEvm, fromGoogle = false)
        substrate.accountNameChanged("Daily")
        assertNull(substrate.showScreenshotsWarningEvent.value)
        substrate.nextClicked()
        assertEquals(Unit, substrate.showScreenshotsWarningEvent.value?.getContentIfNotHandled())
        verify(exactly = 0) { router.openMnemonicScreen(any(), any()) }
        substrate.screenshotWarningConfirmed()
        verify { router.openMnemonicScreen("Daily", listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)) }

        val ton = viewModel(AccountType.Ton)
        ton.accountNameChanged("Toncoin")
        ton.screenshotWarningConfirmed()
        verify { router.openMnemonicScreen("Toncoin", listOf(WalletEcosystem.Ton)) }
    }

    @Test
    fun `a google backup wallet goes to the agreements without the screenshot warning`() = runTest(dispatcher) {
        val vm = viewModel(AccountType.SubstrateOrEvm, fromGoogle = true)
        vm.accountNameChanged("Cloud")
        vm.nextClicked()

        assertNull(vm.showScreenshotsWarningEvent.value)
        verify {
            router.openMnemonicAgreementsDialogForGoogleBackup(
                accountName = "Cloud",
                accountTypes = listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
            )
        }
    }

    @Test
    fun `home returns to welcome, back pops, and the account type is required`() {
        val vm = viewModel(AccountType.Ton)
        vm.homeButtonClicked()
        vm.onBackClick()

        verify { router.backToWelcomeScreen() }
        verify { router.back() }
        assertThrows(IllegalStateException::class.java) {
            CreateAccountViewModel(router, VmTestFixtures.resourceManager(), SavedStateHandle())
        }
    }
}
