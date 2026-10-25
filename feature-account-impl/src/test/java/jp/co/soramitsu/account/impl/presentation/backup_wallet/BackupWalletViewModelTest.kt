package jp.co.soramitsu.account.impl.presentation.backup_wallet

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupWalletViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val detailsInteractor = mockk<AccountDetailsInteractor>()
    private val totalBalanceUseCase = mockk<TotalBalanceUseCase>()
    private val resourceManager = mockk<ResourceManager>()
    private val viewModels = mutableListOf<BackupWalletViewModel>()

    private val ownAddress = "5Own"
    private val phoneCopy = BackupAccountMeta("Wallet", ownAddress, "phone")
    private val tabletCopy = BackupAccountMeta("Wallet", ownAddress, "tablet")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resourceManager.getQuantityString(any(), any(), *anyVararg()) } answers { "plural-${firstArg<Int>()}-${secondArg<Int>()}" }
        every { detailsInteractor.getChainProjectionsFlow(WALLET_ID) } returns flowOf(emptyMap())
        every { totalBalanceUseCase.observe(WALLET_ID) } returns flowOf(portfolio())
        coEvery { interactor.getMetaAccount(WALLET_ID) } returns wallet()
        every { interactor.isGoogleBackupAvailable() } returns true
        coEvery { interactor.getSupportedBackupTypes(WALLET_ID) } returns setOf(BackupAccountType.JSON)
        coEvery { interactor.googleBackupAddressForWallet(WALLET_ID) } returns ownAddress
        coEvery { interactor.getExtensionGoogleBackups() } returns emptyList()
        coEvery { interactor.getGoogleBackupCopies(WALLET_ID) } returns emptyList()
    }

    @After
    fun teardown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `the backup a wallet was imported from is neither shown as its own nor deleted`() = runTest(dispatcher) {
        // A drifted phrase restored another account: the wallet keeps the funded account's backup address.
        val fundedCopy = BackupAccountMeta("Funded", "5Funded", "funded")
        coEvery { interactor.googleBackupAddressForWallet(WALLET_ID) } returns "5Funded"
        coEvery { interactor.getGoogleBackupAccounts() } returns listOf(fundedCopy)
        val vm = checkedViewModel()

        assertFalse(vm.state.value.isWalletSavedInGoogle)

        vm.onDeleteGoogleBackupClick()
        runCurrent()

        assertNull(vm.errorDialogStateLiveData.value)
        coVerify(exactly = 0) { interactor.deleteGoogleBackupAccount(any(), any()) }
    }

    @Test
    fun `deleting several copies says how many and removes exactly those`() = runTest(dispatcher) {
        coEvery { interactor.getGoogleBackupCopies(WALLET_ID) } returns listOf(tabletCopy, phoneCopy)
        val vm = checkedViewModel()
        assertTrue(vm.state.value.isWalletSavedInGoogle)

        vm.onDeleteGoogleBackupClick()
        runCurrent()
        val dialog = vm.dialog()
        assertEquals("plural-${R.plurals.backup_wallet_delete_copies_alert_message}-2", dialog.message)

        // A copy another device adds before the user confirms is not part of what they confirmed.
        coEvery { interactor.getGoogleBackupCopies(WALLET_ID) } returns listOf(BackupAccountMeta("Wallet", ownAddress, "new"), tabletCopy, phoneCopy)
        dialog.positiveClick()
        runCurrent()

        coVerify(exactly = 1) { interactor.deleteGoogleBackupAccount(WALLET_ID, listOf(tabletCopy, phoneCopy)) }
    }

    @Test
    fun `a single copy keeps the original confirmation`() = runTest(dispatcher) {
        coEvery { interactor.getGoogleBackupCopies(WALLET_ID) } returns listOf(phoneCopy)
        val vm = checkedViewModel()

        vm.onDeleteGoogleBackupClick()
        runCurrent()
        val dialog = vm.dialog()
        dialog.positiveClick()
        runCurrent()

        assertEquals("text-${R.string.backup_wallet_delete_alert_message}", dialog.message)
        coVerify(exactly = 1) { interactor.deleteGoogleBackupAccount(WALLET_ID, listOf(phoneCopy)) }
    }

    @Test
    fun `an extension backup is explained instead of deleted`() = runTest(dispatcher) {
        coEvery { interactor.getExtensionGoogleBackups() } returns listOf(BackupAccountMeta("Ext", ownAddress, "web"))
        val vm = checkedViewModel()
        assertTrue(vm.state.value.isWalletSavedInGoogle)

        vm.onDeleteGoogleBackupClick()
        runCurrent()

        assertEquals("text-${R.string.remove_backup_extension_error_message}", vm.dialog().message)
        coVerify(exactly = 0) { interactor.deleteGoogleBackupAccount(any(), any()) }
    }

    @Test
    fun `a wallet whose funds are all hidden shows them before a backup decision, labelled`() = runTest(dispatcher) {
        every { totalBalanceUseCase.observe(WALLET_ID) } returns flowOf(portfolio(hidden = BigDecimal("125"), hiddenCount = 2))
        val vm = checkedViewModel()

        val walletItem = vm.state.value.walletItem
        assertEquals(BigDecimal("125").formatFiat("$"), walletItem.balance)
        assertEquals("text-${CommonR.string.portfolio_balance_incl_hidden}", walletItem.balanceCaption)
    }

    @Test
    fun `nothing hidden means no caption`() = runTest(dispatcher) {
        every { totalBalanceUseCase.observe(WALLET_ID) } returns flowOf(portfolio(shown = BigDecimal("3")))
        val vm = checkedViewModel()

        assertEquals(BigDecimal("3").formatFiat("$"), vm.state.value.walletItem.balance)
        assertNull(vm.state.value.walletItem.balanceCaption)
    }

    private fun portfolio(shown: BigDecimal = BigDecimal.ZERO, hidden: BigDecimal = BigDecimal.ZERO, hiddenCount: Int = 0): TotalBalance.Ready {
        fun holdings(amount: BigDecimal, count: Int) = PortfolioBalance(amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, count, count, 0, 0)
        val shownCount = if (shown.signum() > 0) 1 else 0
        return TotalBalance.Ready(wallet(), emptyMap(), "usd", "$", true,
            holdings(shown, shownCount), holdings(hidden, hiddenCount), holdings(shown + hidden, shownCount + hiddenCount))
    }

    private fun TestScope.checkedViewModel(): BackupWalletViewModel {
        val vm = BackupWalletViewModel(
            accountRouter = mockk<AccountRouter>(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf(BackupWalletDialog.ACCOUNT_ID_KEY to WALLET_ID)),
            accountInteractor = interactor,
            accountDetailsInteractor = detailsInteractor,
            addressIconGenerator = mockk(relaxed = true),
            totalBalanceUseCase = totalBalanceUseCase,
            resourceManager = resourceManager
        ).also { viewModels += it }
        runCurrent()
        vm.onGoogleSignInSuccess()
        runCurrent()
        return vm
    }

    private fun BackupWalletViewModel.dialog(): ErrorDialogState = errorDialogStateLiveData.value!!.peekContent()

    private fun wallet() = MetaAccount(
        id = WALLET_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val WALLET_ID = 3L
    }
}
