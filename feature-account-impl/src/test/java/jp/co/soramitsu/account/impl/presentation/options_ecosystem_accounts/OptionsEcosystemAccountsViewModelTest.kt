package jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId
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
class OptionsEcosystemAccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val details = mockk<AccountDetailsInteractor>()
    private val interactor = mockk<AccountInteractor>()
    private val mnemonicDestination = mockk<DelayedNavigation>()
    private val seedDestination = mockk<DelayedNavigation>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(type: WalletEcosystem, backup: BackupAccountType?): OptionsEcosystemAccountsViewModel {
        coEvery { details.hasReplacedAccounts(4, type) } returns false
        coEvery { interactor.getBestBackupType(4, type) } returns backup
        every { router.getExportMnemonicDestination(4, any()) } returns mnemonicDestination
        every { router.getExportSeedDestination(4, any()) } returns seedDestination
        return OptionsEcosystemAccountsViewModel(
            SavedStateHandle(mapOf(OptionsEcosystemAccountsFragment.KEY_META_ID to 4L, OptionsEcosystemAccountsFragment.KEY_TYPE to type)),
            details, interactor, router
        )
    }

    @Test
    fun `the replaced-accounts flag starts conservative and then follows the wallet`() = runTest(dispatcher) {
        val vm = viewModel(WalletEcosystem.Substrate, null)
        assertTrue(vm.state.value.hasReplacedAccounts)
        runCurrent()
        assertFalse(vm.state.value.hasReplacedAccounts)
        assertEquals(4, vm.state.value.metaId)
        assertEquals(WalletEcosystem.Substrate, vm.state.value.type)
    }

    @Test
    fun `a phrase backup exports the mnemonic of the ecosystem's reference chain behind the pin`() = runTest(dispatcher) {
        viewModel(WalletEcosystem.Substrate, BackupAccountType.PASSPHRASE).onBackupEcosystemAccountsClicked()
        viewModel(WalletEcosystem.Ton, BackupAccountType.PASSPHRASE).onBackupEcosystemAccountsClicked()
        runCurrent()

        verify { router.getExportMnemonicDestination(4, polkadotChainId) }
        verify { router.getExportMnemonicDestination(4, tonChainId) }
        verify(exactly = 2) { router.withPinCodeCheckRequired(mnemonicDestination, pinCodeTitleRes = R.string.account_export) }
    }

    @Test
    fun `a seed backup exports the ethereum seed behind the pin`() = runTest(dispatcher) {
        viewModel(WalletEcosystem.Ethereum, BackupAccountType.SEED).onBackupEcosystemAccountsClicked()
        runCurrent()

        verify { router.getExportSeedDestination(4, ethereumChainId) }
        verify { router.withPinCodeCheckRequired(seedDestination, pinCodeTitleRes = R.string.account_export) }
    }

    @Test
    fun `json, missing backups and ecosystems without an export chain open nothing`() = runTest(dispatcher) {
        viewModel(WalletEcosystem.Substrate, BackupAccountType.JSON).onBackupEcosystemAccountsClicked()
        viewModel(WalletEcosystem.Substrate, null).onBackupEcosystemAccountsClicked()
        viewModel(WalletEcosystem.Bitcoin, BackupAccountType.PASSPHRASE).onBackupEcosystemAccountsClicked()
        viewModel(WalletEcosystem.Solana, BackupAccountType.SEED).onBackupEcosystemAccountsClicked()
        viewModel(WalletEcosystem.Iroha, BackupAccountType.PASSPHRASE).onBackupEcosystemAccountsClicked()
        runCurrent()

        verify(exactly = 0) { router.withPinCodeCheckRequired(any(), any(), any()) }
    }

    @Test
    fun `accounts and back navigate and both keys are required`() {
        val vm = viewModel(WalletEcosystem.Ethereum, null)
        vm.onEcosystemAccountsClicked()
        vm.onBackClicked()

        verify { router.openEcosystemAccountsFragment(4, WalletEcosystem.Ethereum) }
        verify { router.back() }
        assertThrows(IllegalStateException::class.java) {
            OptionsEcosystemAccountsViewModel(SavedStateHandle(), details, interactor, router)
        }
        assertThrows(IllegalStateException::class.java) {
            OptionsEcosystemAccountsViewModel(SavedStateHandle(mapOf(OptionsEcosystemAccountsFragment.KEY_META_ID to 4L)), details, interactor, router)
        }
    }
}
