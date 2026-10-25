package jp.co.soramitsu.account.impl.presentation.create_backup_password

import android.app.Activity
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.presentation.backup.GoogleBackupIssue
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
class CreateBackupPasswordViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>()
    private val viewModels = mutableListOf<CreateBackupPasswordViewModel>()

    // Synthetic input for a mocked interactor, never a wallet phrase.
    private val newWallet = AddAccountPayload.SubstrateOrEvm("New wallet", "synthetic words", CryptoType.SR25519, "", "", null, false)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        coEvery { interactor.createAccount(newWallet) } returns Result.success(42L)
        coEvery { interactor.saveGoogleBackupAccount(any(), any()) } returns emptySet()
    }

    @After
    fun teardown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        payload: SaveBackupPayload = SaveBackupPayload(walletId = null, addAccountPayload = newWallet),
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(CreateBackupPasswordDialog.PAYLOAD_KEY to payload))
    ) = CreateBackupPasswordViewModel(router, savedStateHandle, interactor, resourceManager).also {
        viewModels += it
        it.onOriginPasswordChange("backup password")
        it.onConfirmPasswordChange("backup password")
    }

    private fun CreateBackupPasswordViewModel.dialog(): ErrorDialogState = errorDialogStateLiveData.value!!.peekContent()

    @Test
    fun `a failed upload retries the backup of the created wallet without creating it again`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(42L, "backup password") } throws IOException("offline") andThen emptySet()
        val vm = viewModel()

        vm.onApplyPasswordClick()
        runCurrent()

        val dialog = vm.dialog()
        assertEquals("text-${GoogleBackupIssue.NO_CONNECTION.titleRes}", dialog.title)
        assertTrue(dialog.message.endsWith("text-${R.string.google_backup_wallet_created_not_backed_up}"))
        assertEquals("text-${R.string.common_try_again}", dialog.positiveButtonText)
        assertEquals("text-${R.string.google_backup_back_up_later}", dialog.negativeButtonText)
        assertTrue(vm.isWalletCreated.value)
        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 0) { router.backWithResult(*anyVararg()) }

        dialog.positiveClick()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(any()) }
        coVerify(exactly = 2) { interactor.saveGoogleBackupAccount(42L, "backup password") }
        coVerify(exactly = 1) { interactor.updateWalletBackedUp(42L) }
        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_OK) }
    }

    @Test
    fun `back up later keeps the created wallet unmarked and hands it to the caller`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(42L, any()) } throws IOException("offline")
        val vm = viewModel()

        vm.onApplyPasswordClick()
        runCurrent()
        vm.dialog().negativeClick()

        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 1) {
            router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED)
        }
    }

    @Test
    fun `leaving after the wallet was created hands it over instead of dropping it`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(42L, any()) } throws IOException("offline")
        val vm = viewModel()
        vm.onApplyPasswordClick()
        runCurrent()

        vm.onBackClick()

        verify(exactly = 1) {
            router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to CreateBackupPasswordDialog.RESULT_BACKUP_SKIPPED)
        }
    }

    @Test
    fun `leaving before anything was created is a plain cancel`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onBackClick()

        assertFalse(vm.isWalletCreated.value)
        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_CANCELED) }
    }

    @Test
    fun `a wallet that could not be created is never reported as backed up`() = runTest(dispatcher) {
        coEvery { interactor.createAccount(newWallet) } returns Result.failure(IllegalStateException("storage unavailable"))
        val vm = viewModel()

        vm.onApplyPasswordClick()
        runCurrent()

        assertEquals("text-${GoogleBackupIssue.UNKNOWN.titleRes}", vm.dialog().title)
        // Nothing was created, so the message does not claim a wallet exists.
        assertEquals("text-${GoogleBackupIssue.UNKNOWN.messageRes}", vm.dialog().message)
        assertFalse(vm.isWalletCreated.value)
        coVerify(exactly = 0) { interactor.saveGoogleBackupAccount(any(), any()) }
        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 0) { router.backWithResult(*anyVararg()) }
    }

    @Test
    fun `an existing wallet retries only its upload`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(7L, any()) } throws IOException("offline") andThen emptySet()
        val vm = viewModel(payload = SaveBackupPayload(walletId = 7L, addAccountPayload = null))

        vm.onApplyPasswordClick()
        runCurrent()
        assertEquals("text-${GoogleBackupIssue.NO_CONNECTION.messageRes}", vm.dialog().message)
        vm.dialog().positiveClick()
        runCurrent()

        coVerify(exactly = 0) { interactor.createAccount(any()) }
        coVerify(exactly = 1) { interactor.updateWalletBackedUp(7L) }
        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_OK) }
    }

    @Test
    fun `the created wallet survives a recreated screen`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(42L, any()) } throws IOException("offline") andThen emptySet()
        val savedState = SavedStateHandle(mapOf(CreateBackupPasswordDialog.PAYLOAD_KEY to SaveBackupPayload(null, newWallet)))
        viewModel(savedStateHandle = savedState).onApplyPasswordClick()
        runCurrent()

        val recreated = viewModel(savedStateHandle = savedState)
        assertTrue(recreated.isWalletCreated.value)
        recreated.onApplyPasswordClick()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(any()) }
        coVerify(exactly = 1) { interactor.updateWalletBackedUp(42L) }
    }

    @Test
    fun `a phrase left out of the backup is explained before continuing`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(7L, any()) } returns setOf(BackupAccountType.PASSPHRASE)
        val vm = viewModel(payload = SaveBackupPayload(walletId = 7L, addAccountPayload = null))

        vm.onApplyPasswordClick()
        runCurrent()

        coVerify(exactly = 1) { interactor.updateWalletBackedUp(7L) }
        assertEquals("text-${R.string.google_backup_saved_without_phrase_title}", vm.dialog().title)
        verify(exactly = 0) { router.backWithResult(*anyVararg()) }

        vm.dialog().positiveClick()

        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_OK) }
    }

    @Test
    fun `nothing verified to upload closes with the other backup options`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(7L, any()) } throws BackupSecretNotVerifiedException(BackupAccountType.JSON)
        val vm = viewModel(payload = SaveBackupPayload(walletId = 7L, addAccountPayload = null))

        vm.onApplyPasswordClick()
        runCurrent()

        assertEquals("text-${GoogleBackupIssue.SECRET_NOT_VERIFIED.titleRes}", vm.dialog().title)
        assertNull(vm.dialog().negativeButtonText)
        vm.dialog().positiveClick()

        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_CANCELED) }
    }

    @Test
    fun `missing drive consent is requested and the backup repeated once granted`() = runTest(dispatcher) {
        val consent = mockk<Intent>()
        coEvery { interactor.saveGoogleBackupAccount(7L, any()) } throws AuthConsentException(consent) andThen emptySet()
        val vm = viewModel(payload = SaveBackupPayload(walletId = 7L, addAccountPayload = null))
        val requests = mutableListOf<Intent>()
        vm.requestGoogleConsent.onEach { requests += it.peekContent() }.launchIn(backgroundScope)
        runCurrent()

        vm.onApplyPasswordClick()
        runCurrent()
        assertEquals(listOf(consent), requests)

        vm.onGoogleAuthorized()
        runCurrent()

        coVerify(exactly = 1) { interactor.updateWalletBackedUp(7L) }
        verify(exactly = 1) { router.backWithResult(CreateBackupPasswordDialog.RESULT_BACKUP_KEY to Activity.RESULT_OK) }
    }

    @Test
    fun `an expired sign-in offers signing in again`() = runTest(dispatcher) {
        coEvery { interactor.saveGoogleBackupAccount(7L, any()) } throws UnauthorizedException()
        val vm = viewModel(payload = SaveBackupPayload(walletId = 7L, addAccountPayload = null))
        val signInRequests = mutableListOf<Event<Unit>>()
        vm.requestGoogleSignIn.onEach { signInRequests += it }.launchIn(backgroundScope)
        runCurrent()

        vm.onApplyPasswordClick()
        runCurrent()
        assertEquals("text-${R.string.google_backup_sign_in}", vm.dialog().positiveButtonText)
        vm.dialog().positiveClick()
        runCurrent()

        assertEquals(1, signInRequests.size)
    }

    @Test
    fun `backing out of google sign-in needs no message`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onGoogleSignInFailed(GoogleSignInResult.Cancelled)
        assertNull(vm.errorDialogStateLiveData.value)

        vm.onGoogleSignInFailed(GoogleSignInResult.Network)
        assertEquals("text-${GoogleBackupIssue.NO_CONNECTION.titleRes}", vm.dialog().title)
    }
}
