package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryCenterViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private lateinit var model: RecoveryCenterViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val inventory = mockk<WalletRecoveryInventory>()
        every { inventory.observe() } returns flowOf(WalletRecoveryInventory.State(emptyList(), 0))
        model = RecoveryCenterViewModel(inventory, router)
    }

    @After fun teardown() {
        model.viewModelScope.cancel()
        model.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    @Test fun recoveryRoutesToTheAuthenticatedTargetWhileCorrectedImportCreatesASeparateWallet() {
        val target = RecoveryTarget.from(7, WalletSecretScope.Ethereum)
        model.restore(target, ImportMode.Json)
        verify(exactly = 1) { router.openRebind(target, ImportMode.Json) }
        verify(exactly = 0) { router.openImportAddAccountScreen(any(), any(), any()) }
        model.importCorrected(target)
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Ethereum, ImportMode.MnemonicPhrase) }
    }

    @Test fun networkAdvisoryExportsTheNetworkKeyThroughPinAuthentication() {
        val destination = mockk<jp.co.soramitsu.common.navigation.DelayedNavigation>()
        every { router.openExportJsonPasswordDestination(7, "network-id", false) } returns destination
        model.backupWallet(7, "network-id")
        verify(exactly = 1) { router.withPinCodeCheckRequired(destination, false, jp.co.soramitsu.feature_account_impl.R.string.account_export) }
        verify(exactly = 0) { router.openBackupWalletScreen(any()) }
    }

    @Test fun previousStorageRetryUsesTheDedicatedGateWithoutGrantingWalletAuthentication() {
        model.retryPreviousStorage()
        verify(exactly = 1) { router.openPreviousSecureStorageRecovery() }
        verify(exactly = 0) { router.openMain() }
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test fun unreadablePinActionReusesTheGuardedPinScreen() {
        model.resetPin()
        verify(exactly = 1) { router.openChangePinCode() }
        verify(exactly = 0) { router.openCreatePincode() }
    }

    @Test fun backupAndEmergencyAccessRemainAvailableWithoutMutatingAWallet() {
        model.backupWallet(7, null)
        model.exportStoredData()
        model.archivedData()
        model.back()
        verify(exactly = 1) { router.openBackupWalletScreen(7) }
        verify(exactly = 1) { router.openWalletEmergencyExport() }
        verify(exactly = 1) { router.openArchivedWalletRecovery() }
        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.openCreatePincode() }
    }
    @Test fun savedEndpointsOpenTheirOwnLockedDestinationWithoutSelectingAWalletOrNetwork() {
        model.savedEndpoints()
        verify(exactly = 1) { router.openSavedEndpoints() }
        verify(exactly = 0) { router.openCreatePincode() }
        verify(exactly = 0) { router.openMain() }
    }

}
