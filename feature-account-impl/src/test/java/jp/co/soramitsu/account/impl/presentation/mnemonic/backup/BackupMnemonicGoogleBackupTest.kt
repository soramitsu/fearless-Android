package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.create_backup_password.CreateBackupPasswordDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
class BackupMnemonicGoogleBackupTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val backupService = mockk<BackupService>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val cryptoTypeChooser = mockk<CryptoTypeChooserMixin>(relaxed = true)
    private val viewModels = mutableListOf<BackupMnemonicViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { cryptoTypeChooser.selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("sr25519", CryptoType.SR25519))
        // Synthetic words for a mocked interactor, never a wallet phrase.
        coEvery { interactor.generateMnemonic(any()) } returns listOf("synthetic", "words")
        coEvery { interactor.isCodeSet() } returns false
        every { backupService.isAvailable } returns true
    }

    @After
    fun teardown() = runTest(dispatcher) {
        val jobs = viewModels.mapNotNull { it.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        // A cancelled Default-dispatcher child still has to resume on Main to finish.
        // Keep the test dispatcher installed until every owned child has completed.
        jobs.joinAll()
        Dispatchers.resetMain()
    }

    private fun viewModel() = BackupMnemonicViewModel(
        interactor,
        router,
        cryptoTypeChooser,
        SavedStateHandle(
            mapOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(
                    isFromGoogleBackup = false,
                    accountName = "New wallet",
                    walletId = null,
                    accountTypes = listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
                )
            )
        ),
        backupService,
        resourceManager
    ).also { viewModels += it }

    @Test
    fun `a wallet created without its google backup still continues into the app`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns flowOf(CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED)
        val vm = viewModel()
        runCurrent()

        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 1) { router.openCreatePincode() }
        // The wallet was created by the password screen; nothing here creates it again.
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test
    fun `a backed-up wallet continues into the app`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns flowOf(Activity.RESULT_OK)
        val vm = viewModel()
        runCurrent()

        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 1) { router.openCreatePincode() }
    }

    @Test
    fun `cancelling before a wallet exists stays on the phrase`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns flowOf(Activity.RESULT_CANCELED)
        val vm = viewModel()
        runCurrent()

        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 0) { router.openCreatePincode() }
        verify(exactly = 0) { router.openMain() }
    }

    @Test
    fun `google backup is not offered when this build cannot sign in`() = runTest(dispatcher) {
        assertTrue(viewModel().isShowBackupWithGoogle)

        every { backupService.isAvailable } returns false

        assertFalse(viewModel().isShowBackupWithGoogle)
    }

    @Test
    fun `a failed google sign-in is explained and backing out is not`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        assertNull(vm.errorDialogStateLiveData.value)

        vm.onGoogleSignInFailed(GoogleSignInResult.ConfigurationError)
        assertEquals("text-${GoogleBackupIssue.UNAVAILABLE.titleRes}", vm.errorDialogStateLiveData.value!!.peekContent().title)
    }
}
