package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.create_backup_password.CreateBackupPasswordDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    private val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
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

    private fun viewModel(isFromGoogleBackup: Boolean = false) = BackupMnemonicViewModel(
        interactor,
        router,
        cryptoTypeChooser,
        SavedStateHandle(
            mapOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(
                    isFromGoogleBackup = isFromGoogleBackup,
                    accountName = "New wallet",
                    walletId = null,
                    accountTypes = listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum)
                )
            )
        ),
        backupService,
        resourceManager
    ).also { viewModels += it }

    // The phrase is generated off the main thread; the screen offers nothing until it is shown.
    private suspend fun shownViewModel(isFromGoogleBackup: Boolean = false) =
        viewModel(isFromGoogleBackup).also { it.mnemonic.first { words -> words.isNotEmpty() } }

    // The relaxed backup service launches the account picker, so the result arrives as a callback.
    private fun BackupMnemonicViewModel.startGoogleBackup(substratePath: String = "", ethereumPath: String = "") {
        onGoogleBackupClick(substratePath, ethereumPath, launcher)
    }

    @Test
    fun `a wallet created without its google backup still continues into the app`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns flowOf(CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED)
        val vm = shownViewModel()
        vm.startGoogleBackup()
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
        val vm = shownViewModel()
        vm.startGoogleBackup()
        runCurrent()

        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 1) { router.openCreatePincode() }
    }

    @Test
    fun `cancelling before a wallet exists stays on the phrase`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns flowOf(Activity.RESULT_CANCELED)
        val vm = shownViewModel()
        vm.startGoogleBackup()
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

    @Test
    fun `google backup creates the wallet with the derivation paths the user entered`() = runTest(dispatcher) {
        val requests = mutableListOf<SaveBackupPayload>()
        every { router.openCreateBackupPasswordDialogWithResult(any()) } answers {
            requests += firstArg<SaveBackupPayload>()
            emptyFlow()
        }
        val vm = shownViewModel()

        vm.startGoogleBackup(substratePath = "//backup", ethereumPath = "//44//60//0/0/1")
        runCurrent()
        vm.onGoogleSignInSuccess()
        runCurrent()

        assertEquals(
            AddAccountPayload.SubstrateOrEvm("New wallet", "synthetic words", CryptoType.SR25519, "//backup", "//44//60//0/0/1", null, false, freshlyGenerated = true),
            requests.single().addAccountPayload
        )
    }

    @Test
    fun `a sign-in result this screen did not ask for starts no backup`() = runTest(dispatcher) {
        // Nothing loaded yet: the phrase and its key type arrive asynchronously.
        every { cryptoTypeChooser.selectedEncryptionTypeLiveData } returns MutableLiveData()
        val vm = viewModel()

        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 0) { router.openCreateBackupPasswordDialogWithResult(any()) }
    }

    @Test
    fun `repeated sign-in results open one password screen`() = runTest(dispatcher) {
        every { router.openCreateBackupPasswordDialogWithResult(any()) } returns emptyFlow()
        val vm = shownViewModel()

        vm.startGoogleBackup()
        vm.startGoogleBackup()
        runCurrent()
        vm.onGoogleSignInSuccess()
        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 1) { router.openCreateBackupPasswordDialogWithResult(any()) }
    }

    @Test
    fun `a cancelled sign-in leaves nothing for a later result to open`() = runTest(dispatcher) {
        val vm = shownViewModel()
        vm.startGoogleBackup()
        runCurrent()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        vm.onGoogleSignInSuccess()
        runCurrent()

        verify(exactly = 0) { router.openCreateBackupPasswordDialogWithResult(any()) }
    }

    @Test
    fun `google backup does not start before the phrase is shown`() = runTest(dispatcher) {
        coEvery { interactor.generateMnemonic(any()) } coAnswers { awaitCancellation() }
        val vm = viewModel()
        runCurrent()

        vm.startGoogleBackup()
        runCurrent()

        coVerify(exactly = 0) { backupService.authorize(any()) }
        verify(exactly = 0) { router.openCreateBackupPasswordDialogWithResult(any()) }
    }

    @Test
    fun `continuing from the google backup screen backs up the shown wallet once`() = runTest(dispatcher) {
        // Already signed in to Google: no sign-in result follows.
        coEvery { backupService.authorize(any()) } returns true
        val requests = mutableListOf<SaveBackupPayload>()
        every { router.openCreateBackupPasswordDialogWithResult(any()) } answers {
            requests += firstArg<SaveBackupPayload>()
            emptyFlow()
        }
        val vm = shownViewModel(isFromGoogleBackup = true)

        vm.onNextClick(launcher)
        runCurrent()
        vm.onGoogleSignInSuccess()
        runCurrent()

        coVerify(exactly = 0) { backupService.logout() }
        assertEquals(
            AddAccountPayload.SubstrateOrEvm("New wallet", "synthetic words", CryptoType.SR25519, "", BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH, null, false, freshlyGenerated = true),
            requests.single().addAccountPayload
        )
    }
}
