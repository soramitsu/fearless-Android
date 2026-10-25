package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.BackupOrigin
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.labelRes
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.BackupUnavailableException
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class ImportRemoteWalletViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val backupService = mockk<BackupService>()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val viewModels = mutableListOf<ImportRemoteWalletViewModel>()

    private val appBackup = BackupAccountMeta(name = "App wallet", address = "5App", fileId = "a1")

    // Synthetic content for a mocked backup service, never a wallet phrase.
    private val phraseBackup = DecryptedBackupAccount(
        name = "App wallet",
        address = "5App",
        mnemonicPhrase = "synthetic words",
        cryptoType = CryptoType.ED25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED),
        seed = Seed(substrateSeed = "0x01", ethSeed = null)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        coEvery { backupService.getBackupAccounts() } returns listOf(appBackup)
        coEvery { backupService.getWebBackupAccounts() } returns emptyList()
        coEvery { backupService.restoringSecretTypes(any()) } returns null
        coEvery { backupService.logout() } returns Unit
        coEvery { interactor.createAccount(any()) } returns Result.success(1L)
        coEvery { interactor.importFromSeed(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { interactor.importFromJson(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { interactor.isCodeSet() } returns true
    }

    @After
    fun teardown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel() = ImportRemoteWalletViewModel(router, backupService, interactor, resourceManager).also { viewModels += it }

    private fun ImportRemoteWalletViewModel.listState() = state.value as RemoteWalletListState

    private fun ImportRemoteWalletViewModel.dialog(): ErrorDialogState = errorDialogStateLiveData.value!!.peekContent()

    @Test
    fun `a failing extension list shows a retryable error instead of crashing`() = runTest(dispatcher) {
        coEvery { backupService.getWebBackupAccounts() } throws IOException("offline") andThen emptyList()
        val vm = viewModel()

        vm.loadRemoteWallets()
        runCurrent()

        val error = vm.listState().error!!
        assertEquals("text-${GoogleBackupIssue.NO_CONNECTION.titleRes}", error.title)
        assertEquals("text-${R.string.common_try_again}", error.actionText)
        assertNull(vm.listState().wallets)

        vm.onErrorActionClick()
        runCurrent()

        assertNull(vm.listState().error)
        assertEquals(listOf(appBackup), vm.listState().wallets!!.map { it.backupMeta })
    }

    @Test
    fun `an expired sign-in on the extension list offers signing in again`() = runTest(dispatcher) {
        coEvery { backupService.getWebBackupAccounts() } throws UnauthorizedException()
        coEvery { backupService.authorize(any()) } returns false
        val vm = viewModel()
        val signInRequests = collect(vm)

        vm.loadRemoteWallets()
        runCurrent()
        assertEquals("text-${R.string.google_backup_sign_in}", vm.listState().error!!.actionText)

        vm.onErrorActionClick()
        runCurrent()
        assertEquals(1, signInRequests.size)

        vm.signInToGoogle(mockk())
        runCurrent()
        coVerify(exactly = 1) { backupService.logout() }
        coVerify(exactly = 1) { backupService.authorize(any()) }
    }

    @Test
    fun `missing drive consent on the extension list opens the consent screen`() = runTest(dispatcher) {
        val consent = mockk<Intent>()
        coEvery { backupService.getWebBackupAccounts() } throws AuthConsentException(consent)
        val vm = viewModel()
        val consentRequests = mutableListOf<Intent>()
        vm.requestGoogleAuth.onEach { consentRequests += it.peekContent() }.launchIn(backgroundScope)
        runCurrent()

        vm.loadRemoteWallets()
        runCurrent()

        assertEquals(listOf(consent), consentRequests)
        assertNull(vm.listState().error)
    }

    @Test
    fun `closing the consent screen leaves a way back instead of an empty list`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        runCurrent()

        assertEquals("text-${GoogleBackupIssue.SIGN_IN_REQUIRED.titleRes}", vm.listState().error!!.title)
    }

    @Test
    fun `a build without google sign-in explains and offers the other imports`() = runTest(dispatcher) {
        coEvery { backupService.getBackupAccounts() } throws BackupUnavailableException()
        val vm = viewModel()

        vm.loadRemoteWallets()
        runCurrent()
        val error = vm.listState().error!!
        vm.onImportAnotherWayClick()

        assertEquals("text-${GoogleBackupIssue.UNAVAILABLE.messageRes}", error.message)
        assertNull(error.actionText)
        verify(exactly = 1) { router.openImportAccountScreen(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase) }
    }

    @Test
    fun `an empty backup list offers signing in with another google account`() = runTest(dispatcher) {
        val otherAccountBackup = BackupAccountMeta(name = "Other account wallet", address = "5Other", fileId = "o1")
        coEvery { backupService.getBackupAccounts() } returns emptyList() andThen listOf(otherAccountBackup)
        coEvery { backupService.authorize(any()) } returns true
        val vm = viewModel()
        val signInRequests = collect(vm)

        vm.loadRemoteWallets()
        runCurrent()
        assertEquals(emptyList<WrappedBackupAccountMeta>(), vm.listState().wallets)
        assertNull(vm.listState().error)

        vm.onUseAnotherGoogleAccountClick()
        runCurrent()
        assertEquals(1, signInRequests.size)

        vm.signInToGoogle(mockk())
        runCurrent()

        coVerify(exactly = 1) { backupService.logout() }
        assertEquals(listOf(otherAccountBackup), vm.listState().wallets!!.map { it.backupMeta })
    }

    @Test
    fun `backing out of the google account choice leaves a way to sign in`() = runTest(dispatcher) {
        coEvery { backupService.getBackupAccounts() } returns emptyList()
        coEvery { backupService.authorize(any()) } returns false
        val vm = viewModel()

        vm.loadRemoteWallets()
        runCurrent()
        vm.onUseAnotherGoogleAccountClick()
        vm.signInToGoogle(mockk())
        runCurrent()
        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        runCurrent()

        val error = vm.listState().error!!
        assertEquals("text-${GoogleBackupIssue.SIGN_IN_REQUIRED.titleRes}", error.title)
        assertEquals("text-${R.string.google_backup_sign_in}", error.actionText)
    }

    @Test
    fun `substrate-only extension backups are listed apart by file id`() = runTest(dispatcher) {
        val substrateOnlyA = BackupAccountMeta(name = "Extension A", address = "", fileId = "w1")
        val substrateOnlyB = BackupAccountMeta(name = "Extension B", address = "", fileId = "w2")
        val exported = BackupAccountMeta(name = "Exported", address = "5Web", fileId = "w3")
        val exportedAgain = BackupAccountMeta(name = "Exported", address = "5Web", fileId = "w4")
        val alsoInApp = BackupAccountMeta(name = "App wallet", address = "5App", fileId = "w5")
        coEvery { backupService.getWebBackupAccounts() } returns listOf(substrateOnlyA, substrateOnlyB, exported, exportedAgain, alsoInApp)
        val vm = viewModel()

        vm.loadRemoteWallets()
        runCurrent()

        val wallets = vm.listState().wallets!!
        assertEquals(listOf(substrateOnlyA, substrateOnlyB, exported, appBackup), wallets.map { it.backupMeta })
        assertEquals(listOf(BackupOrigin.WEB, BackupOrigin.WEB, BackupOrigin.WEB, BackupOrigin.APP), wallets.map { it.origin })
    }

    @Test
    fun `extension backups are labelled so the extension password is entered`() = runTest(dispatcher) {
        val extensionBackup = BackupAccountMeta(name = "Extension wallet", address = "5Web", fileId = "w1")
        coEvery { backupService.getWebBackupAccounts() } returns listOf(extensionBackup)
        val vm = viewModel()

        vm.loadRemoteWallets()
        runCurrent()

        val labels = vm.listState().wallets!!.associate { it.backupMeta.name to it.origin.labelRes }
        assertEquals(mapOf("Extension wallet" to CommonR.string.google_backup_origin_extension, "App wallet" to null), labels)
    }

    @Test
    fun `an extension backup is imported through its own file id`() = runTest(dispatcher) {
        val substrateOnlyB = BackupAccountMeta(name = "Extension B", address = "", fileId = "w2")
        val keystore = """{"address":"5Web"}"""
        coEvery { backupService.importWebBackupAccount(substrateOnlyB) } returns DecryptedBackupAccount(
            name = "Extension B",
            address = "",
            cryptoType = CryptoType.ED25519,
            backupAccountType = listOf(BackupAccountType.JSON),
            json = Json(substrateJson = keystore, ethJson = null)
        )
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(substrateOnlyB, BackupOrigin.WEB), "extension password")

        verify(exactly = 1) { interactor.validateJsonBackup(keystore, "extension password") }
        coVerify(exactly = 1) { interactor.importFromJson(null, keystore, "extension password", "Extension B", null, "") }
        assertTrue(vm.state.value is WalletImportedState)
    }

    @Test
    fun `a drifted phrase in a downloaded backup is passed over for its seed`() = runTest(dispatcher) {
        coEvery { backupService.importBackupAccount("5App", "pw") } returns phraseBackup
        coEvery { backupService.restoringSecretTypes(phraseBackup) } returns setOf(BackupAccountType.SEED)
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")

        coVerify(exactly = 1) { interactor.importFromSeed(null, "0x01", "App wallet", "", CryptoType.ED25519, null, "5App") }
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test
    fun `import failures are explained by what went wrong`() = runTest(dispatcher) {
        val cases = listOf(
            DecryptionException() to GoogleBackupIssue.WRONG_PASSWORD,
            DecodingException() to GoogleBackupIssue.UNSUPPORTED_BACKUP,
            IOException("offline") to GoogleBackupIssue.NO_CONNECTION,
            UnauthorizedException() to GoogleBackupIssue.SIGN_IN_REQUIRED,
            FileNotFoundException() to GoogleBackupIssue.BACKUP_NOT_FOUND
        )

        cases.forEach { (failure, issue) ->
            coEvery { backupService.importBackupAccount("5App", "pw") } throws failure
            val vm = viewModel()

            importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")

            assertEquals(failure.javaClass.simpleName, "text-${issue.titleRes}", vm.dialog().title)
            assertEquals(failure.javaClass.simpleName, "text-${issue.messageRes}", vm.dialog().message)
            assertTrue(vm.state.value is EnterBackupPasswordState)
        }
    }

    @Test
    fun `a wrong json password is reported as a wrong password`() = runTest(dispatcher) {
        coEvery { backupService.importBackupAccount("5App", "pw") } returns phraseBackup.copy(
            backupAccountType = listOf(BackupAccountType.JSON),
            json = Json(substrateJson = "{}", ethJson = null)
        )
        every { interactor.validateJsonBackup(any(), any()) } throws JsonSeedDecodingException.IncorrectPasswordException()
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")

        assertEquals("text-${GoogleBackupIssue.WRONG_PASSWORD.titleRes}", vm.dialog().title)
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test
    fun `a wallet already on the device opens it instead of failing`() = runTest(dispatcher) {
        coEvery { backupService.importBackupAccount("5App", "pw") } returns phraseBackup.copy(seed = null)
        coEvery { interactor.createAccount(any()) } returns Result.failure(AccountAlreadyExistsException())
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")
        assertEquals("text-${GoogleBackupIssue.ALREADY_ON_DEVICE.titleRes}", vm.dialog().title)
        assertEquals("text-${R.string.google_backup_open_wallet}", vm.dialog().positiveButtonText)
        vm.dialog().positiveClick()
        runCurrent()

        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun `a connection failure during import can be retried with the same password`() = runTest(dispatcher) {
        coEvery { backupService.importBackupAccount("5App", "pw") } throws IOException("offline") andThen phraseBackup.copy(seed = null)
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")
        vm.dialog().positiveClick()
        runCurrent()

        coVerify(exactly = 2) { backupService.importBackupAccount("5App", "pw") }
        coVerify(exactly = 1) { interactor.createAccount(any()) }
        assertTrue(vm.state.value is WalletImportedState)
    }

    @Test
    fun `a backup removed from drive returns to a refreshed list`() = runTest(dispatcher) {
        coEvery { backupService.importBackupAccount("5App", "pw") } throws FileNotFoundException()
        val vm = viewModel()

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")
        assertEquals("text-${R.string.google_backup_refresh}", vm.dialog().positiveButtonText)
        vm.dialog().positiveClick()
        runCurrent()

        assertTrue(vm.state.value is RemoteWalletListState)
    }

    @Test
    fun `missing consent during import is requested and the import repeated once granted`() = runTest(dispatcher) {
        val consent = mockk<Intent>()
        coEvery { backupService.importBackupAccount("5App", "pw") } throws AuthConsentException(consent) andThen phraseBackup.copy(seed = null)
        val vm = viewModel()
        val consentRequests = mutableListOf<Intent>()
        vm.requestGoogleAuth.onEach { consentRequests += it.peekContent() }.launchIn(backgroundScope)

        importWith(vm, WrappedBackupAccountMeta(appBackup), "pw")
        assertEquals(listOf(consent), consentRequests)

        vm.onGoogleAuthorized()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(any()) }
        assertTrue(vm.state.value is WalletImportedState)
    }

    private fun TestScope.importWith(vm: ImportRemoteWalletViewModel, wallet: WrappedBackupAccountMeta, password: String) {
        runCurrent()
        vm.onWalletSelected(wallet)
        vm.onPasswordChanged(password)
        runCurrent()
        vm.onContinueClick()
        runCurrent()
    }

    private fun TestScope.collect(vm: ImportRemoteWalletViewModel): MutableList<Unit> {
        val requests = mutableListOf<Unit>()
        vm.requestGoogleSignIn.onEach { requests += it.peekContent() }.launchIn(backgroundScope)
        runCurrent()
        return requests
    }
}
