package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.content.Intent
import android.graphics.drawable.PictureDrawable
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The full wallet selector: list failures, selection and options, Google backup import and the QR import. */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectWalletViewModelCoverageTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AccountRepository>()
    private val accounts = mockk<AccountInteractor>(relaxed = true)
    private val scores = mockk<NomisScoreInteractor>()
    private val total = mockk<TotalBalanceUseCase>()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val icon = mockk<PictureDrawable>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val pendulum = mockk<PendulumPreInstalledAccountsScenario>()
    private val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
    private val roster = MutableStateFlow<List<LightMetaAccount>>(listOf(ready("First").meta))
    private val models = mutableListOf<SelectWalletViewModel>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.lightMetaAccountsFlow() } returns roster
        every { total.observe(7) } returns MutableStateFlow<TotalBalance>(ready("First"))
        every { scores.observeNomisScores() } returns MutableStateFlow<List<NomisScoreData>>(emptyList())
        every { resources.getString(any()) } answers { text(firstArg()) }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
        coEvery { icons.createWalletIcon(any(), any()) } returns icon
    }

    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun `an unreadable score source keeps the wallets without scores`() = runTest(dispatcher) {
        every { scores.observeNomisScores() } returns failing("scores unreadable")
        val vm = create(); runCurrent()

        val row = vm.state.value.wallets.single()
        assertEquals("First", row.title)
        assertNull(row.score)
        assertEquals(row, vm.state.value.selectedWallet)
    }

    @Test fun `an unreadable wallet roster empties the list and reports the error`() = runTest(dispatcher) {
        every { repository.lightMetaAccountsFlow() } returns failing("roster unreadable")
        val vm = create(); runCurrent()

        assertTrue(vm.state.value.wallets.isEmpty())
        assertNull(vm.state.value.selectedWallet)
        assertEquals("roster unreadable", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `picking a wallet selects it and closes the selector`() = runTest(dispatcher) {
        val vm = create(); runCurrent()

        vm.onWalletSelected(item(9L))
        runCurrent()

        coVerify { accounts.selectMetaAccount(9L) }
        verify { router.back() }
    }

    @Test fun `the selector's buttons route to their screens`() = runTest(dispatcher) {
        val vm = create(); runCurrent()

        vm.addNewWallet()
        vm.onBackClicked()
        vm.onWalletOptionsClick(item(5L))
        vm.onScoreClick(item(6L))

        verify { router.openCreateAccountFromWallet() }
        verify { router.back() }
        verify { router.openOptionsWallet(5L, true) }
        verify { router.openScoreDetailsScreen(6L) }
    }

    @Test fun `without Google backup support the user is offered another way to add a wallet`() = runTest(dispatcher) {
        every { accounts.isGoogleBackupAvailable() } returns false
        val vm = create(); runCurrent()

        vm.authorizeGoogle(launcher)

        val dialog = vm.errorDialogStateLiveData.value!!.peekContent()
        assertEquals(text(GoogleBackupIssue.UNAVAILABLE.titleRes), dialog.title)
        assertEquals(text(GoogleBackupIssue.UNAVAILABLE.messageRes), dialog.message)
        assertEquals(text(R.string.google_backup_import_another_way), dialog.positiveButtonText)
        assertEquals(text(R.string.common_close), dialog.negativeButtonText)
        coVerify(exactly = 0) { accounts.authorizeGoogleBackup(any()) }
        dialog.positiveClick()
        verify { router.openCreateAccountFromWallet() }
    }

    @Test fun `an authorized Google account opens the remote wallet import`() = runTest(dispatcher) {
        every { accounts.isGoogleBackupAvailable() } returns true
        coEvery { accounts.authorizeGoogleBackup(launcher) } returns true
        coEvery { accounts.getGoogleBackupAccounts() } returns emptyList()
        val vm = create(); runCurrent()

        vm.authorizeGoogle(launcher)
        runCurrent()

        verify(exactly = 1) { router.openImportRemoteWalletDialog() }
        assertNull(vm.errorDialogStateLiveData.value)
    }

    @Test fun `a connection failure while reading backups can be retried or replaced by another import`() = runTest(dispatcher) {
        coEvery { accounts.getGoogleBackupAccounts() } throws IOException("offline")
        val vm = create(); runCurrent()

        vm.openAddWalletThroughGoogleScreen()
        runCurrent()

        val dialog = vm.errorDialogStateLiveData.value!!.peekContent()
        assertEquals(text(GoogleBackupIssue.NO_CONNECTION.titleRes), dialog.title)
        assertEquals(text(R.string.common_try_again), dialog.positiveButtonText)
        assertEquals(text(R.string.google_backup_import_another_way), dialog.negativeButtonText)
        verify(exactly = 0) { router.openImportRemoteWalletDialog() }

        dialog.negativeClick()
        verify { router.openCreateAccountFromWallet() }

        coEvery { accounts.getGoogleBackupAccounts() } returns emptyList()
        dialog.positiveClick()
        runCurrent()
        verify(exactly = 1) { router.openImportRemoteWalletDialog() }
    }

    @Test fun `a sign-in the user cancelled needs no explanation, a refused Drive access does`() = runTest(dispatcher) {
        val vm = create(); runCurrent()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        assertNull(vm.errorDialogStateLiveData.value)

        vm.onGoogleSignInFailed(GoogleSignInResult.DriveAccessDenied)
        val dialog = vm.errorDialogStateLiveData.value!!.peekContent()
        assertEquals(text(GoogleBackupIssue.SIGN_IN_REQUIRED.titleRes), dialog.title)
        assertEquals(text(R.string.google_backup_import_another_way), dialog.positiveButtonText)
    }

    @Test fun `an unreadable QR code is reported`() = runTest(dispatcher) {
        val vm = create(); runCurrent()

        vm.onQrScanResult(null)

        assertEquals("Can't scan qr code", vm.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { pendulum.import(any()) }
    }

    @Test fun `a scanned pre-installed account is imported, or its failure shown`() = runTest(dispatcher) {
        coEvery { pendulum.import("good") } answers { Result.success(Unit) }
        coEvery { pendulum.import("bad") } answers { Result.failure(IllegalStateException("not a pre-installed account")) }
        val vm = create(); runCurrent()

        vm.onQrScanResult("bad")
        runCurrent()
        assertEquals("not a pre-installed account", vm.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.back() }

        vm.onQrScanResult("good")
        runCurrent()
        verify(exactly = 1) { router.back() }
    }

    private fun create() = SelectWalletViewModel(repository, icons, accounts, scores, router, total, resources, pendulum)
        .also(models::add)

    private fun <T> failing(message: String): Flow<T> = flow { throw IllegalStateException(message) }

    private fun item(id: Long) = WalletItemViewState(id = id, title = "Wallet $id", walletIcon = icon, isSelected = false)

    private fun ready(name: String): TotalBalance.Ready {
        val key = ByteArray(32) { 1 }
        val meta = MetaAccount(7, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
            null, null, null, true, true, null, name, true)
        val amount = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), "usd", "$", true, amount, hidden, amount)
    }

    private companion object {
        fun text(id: Int) = "text-$id"
    }
}
