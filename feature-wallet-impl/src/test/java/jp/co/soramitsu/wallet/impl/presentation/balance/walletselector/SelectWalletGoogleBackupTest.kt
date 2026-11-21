package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * "Add wallet from Google" lists the Drive backups before opening the import dialog. When that lookup was cancelled
 * (the wallet list closed, the screen left), the cancellation was caught and shown as a Google backup error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectWalletGoogleBackupTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val accounts = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<WalletRouter>(relaxed = true)
    private var model: SelectWalletViewModel? = null

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun teardown() {
        model?.coroutineContext?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a cancelled backup lookup is not reported as a Google backup issue`() {
        coEvery { accounts.getGoogleBackupAccounts() } throws CancellationException("wallet list closed")
        val vm = viewModel()

        vm.openAddWalletThroughGoogleScreen()

        assertNull(vm.errorDialogStateLiveData.value)
        verify(exactly = 0) { router.openImportRemoteWalletDialog() }
    }

    @Test
    fun `a failed backup lookup is still reported`() {
        coEvery { accounts.getGoogleBackupAccounts() } throws IOException("offline")
        val vm = viewModel()

        vm.openAddWalletThroughGoogleScreen()

        assertNotNull(vm.errorDialogStateLiveData.value)
        verify(exactly = 0) { router.openImportRemoteWalletDialog() }
    }

    @Test
    fun `listed backups open the import dialog`() {
        coEvery { accounts.getGoogleBackupAccounts() } returns listOf(BackupAccountMeta("Wallet", "5Address", "file"))
        val vm = viewModel()

        vm.openAddWalletThroughGoogleScreen()

        assertNull(vm.errorDialogStateLiveData.value)
        verify(exactly = 1) { router.openImportRemoteWalletDialog() }
    }

    private fun viewModel(): SelectWalletViewModel {
        val repository = mockk<AccountRepository> { every { lightMetaAccountsFlow() } returns flowOf(emptyList()) }
        return SelectWalletViewModel(repository, mockk(relaxed = true), accounts, router, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true)).also { model = it }
    }
}
