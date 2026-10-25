package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
class ConfirmMnemonicRetryTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val viewModels = mutableListOf<ConfirmMnemonicViewModel>()
    private val buttonObserver = Observer<Boolean> { }
    // Synthetic input for a mocked account interactor, never a wallet seed.
    private val words = listOf("first", "second", "third")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.isCodeSet() } returns true
    }

    @After
    fun teardown() {
        viewModels.forEach {
            it.nextButtonEnableLiveData.removeObserver(buttonObserver)
            it.skipButtonEnableLiveData.removeObserver(buttonObserver)
            it.coroutineContext.cancel()
        }
        Dispatchers.resetMain()
    }

    private fun viewModel(existingWallet: Long? = null, ecosystem: WalletEcosystem = WalletEcosystem.Substrate): ConfirmMnemonicViewModel {
        val extras = if (existingWallet == null) {
            ConfirmMnemonicPayload.CreateExtras("Retry fixture", CryptoType.SR25519, "//test", "m/44'/60'/0'/0/0")
        } else null
        val payload = ConfirmMnemonicPayload(words, existingWallet, extras, listOf(ecosystem))
        return ConfirmMnemonicViewModel(
            mockk(relaxed = true), interactor, router, mockk(relaxed = true),
            SavedStateHandle(mapOf(ConfirmMnemonicFragment.KEY_PAYLOAD to payload))
        ).also {
            viewModels += it
            it.nextButtonEnableLiveData.observeForever(buttonObserver)
            it.skipButtonEnableLiveData.observeForever(buttonObserver)
        }
    }

    @Test
    fun returnedCreateFailurePreservesWordsAndAllowsContinueRetry() = runTest(dispatcher) {
        coEvery { interactor.createAccount(any()) } returnsMany listOf(
            Result.failure(IllegalStateException("Save unavailable")), Result.success(41L)
        )
        val vm = viewModel()
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        runCurrent()
        assertEquals("Save unavailable", vm.errorLiveData.value?.getContentIfNotHandled())
        assertEquals(true, vm.nextButtonEnableLiveData.value)
        assertEquals(true, vm.skipButtonEnableLiveData.value)
        verify(exactly = 0) { router.openMain() }

        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 2) {
            interactor.createAccount(AddAccountPayload.SubstrateOrEvm(
                "Retry fixture", words.joinToString(" "), CryptoType.SR25519,
                "//test", "m/44'/60'/0'/0/0", null, true
            ))
        }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun thrownCreateFailureAllowsTonSkipRetryWithoutClaimingBackup() = runTest(dispatcher) {
        var attempts = 0
        coEvery { interactor.createAccount(any()) } coAnswers {
            if (attempts++ == 0) throw IllegalStateException("Persistence unavailable")
            Result.success(42L)
        }
        coEvery { interactor.isCodeSet() } returns false
        val vm = viewModel(ecosystem = WalletEcosystem.Ton)
        vm.skipClicked()
        vm.errorDialogStateLiveData.value!!.peekContent().positiveClick()
        runCurrent()
        assertEquals("Persistence unavailable", vm.errorLiveData.value?.getContentIfNotHandled())
        assertEquals(true, vm.skipButtonEnableLiveData.value)
        assertEquals(false, vm.nextButtonEnableLiveData.value)

        vm.skipClicked()
        vm.errorDialogStateLiveData.value!!.peekContent().positiveClick()
        runCurrent()
        coVerify(exactly = 2) { interactor.createAccount(AddAccountPayload.Ton("Retry fixture", words.joinToString(" "), false)) }
        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 1) { router.openCreatePincode() }
    }

    @Test
    fun backupPersistenceFailureAllowsExistingWalletRetry() = runTest(dispatcher) {
        var attempts = 0
        coEvery { interactor.updateWalletBackedUp(43L) } coAnswers {
            if (attempts++ == 0) throw IllegalStateException("Backup status unavailable")
        }
        val vm = viewModel(existingWallet = 43L)
        assertFalse(vm.skipVisible)
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        runCurrent()
        assertEquals("Backup status unavailable", vm.errorLiveData.value?.getContentIfNotHandled())
        assertEquals(true, vm.nextButtonEnableLiveData.value)
        verify(exactly = 0) { router.finishExportFlow() }

        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 2) { interactor.updateWalletBackedUp(43L) }
        coVerify(exactly = 0) { interactor.createAccount(any()) }
        verify(exactly = 1) { router.finishExportFlow() }
        assertEquals("Success", vm.messageLiveData.value?.peekContent())
    }

    @Test
    fun duplicateSubmissionsStaySuppressedWhilePendingAndAfterNavigation() = runTest(dispatcher) {
        val result = CompletableDeferred<Result<Long>>()
        coEvery { interactor.createAccount(any()) } coAnswers { result.await() }
        val vm = viewModel()
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        vm.nextButtonClicked()
        runCurrent()
        assertEquals(false, vm.nextButtonEnableLiveData.value)
        assertEquals(false, vm.skipButtonEnableLiveData.value)
        coVerify(exactly = 1) { interactor.createAccount(any()) }

        result.complete(Result.success(44L))
        runCurrent()
        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 1) { interactor.createAccount(any()) }
        verify(exactly = 1) { router.openMain() }
        assertEquals(false, vm.nextButtonEnableLiveData.value)
    }

    @Test
    fun cancellationReleasesBusyStateWithoutShowingAnError() = runTest(dispatcher) {
        var attempts = 0
        coEvery { interactor.createAccount(any()) } coAnswers {
            if (attempts++ == 0) throw CancellationException("Operation cancelled")
            Result.success(45L)
        }
        val vm = viewModel()
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        runCurrent()
        assertNull(vm.errorLiveData.value)
        assertEquals(true, vm.nextButtonEnableLiveData.value)

        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 2) { interactor.createAccount(any()) }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun completionFailureRetriesSavedWalletWithoutCreatingDuplicate() = runTest(dispatcher) {
        coEvery { interactor.createAccount(any()) } returns Result.success(46L)
        var attempts = 0
        coEvery { interactor.saveChainSelectFilter(46L, "Popular") } coAnswers {
            if (attempts++ == 0) throw IllegalStateException("Filter unavailable")
        }
        val vm = viewModel()
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        runCurrent()
        assertEquals("Filter unavailable", vm.errorLiveData.value?.getContentIfNotHandled())
        assertEquals(true, vm.nextButtonEnableLiveData.value)
        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 1) { interactor.createAccount(any()) }
        coVerify(exactly = 2) { interactor.saveChainSelectFilter(46L, "Popular") }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun confirmingAfterSkippedWalletCompletionFailureUpdatesBackupWithoutRecreation() = runTest(dispatcher) {
        coEvery { interactor.createAccount(any()) } returns Result.success(47L)
        var attempts = 0
        coEvery { interactor.isCodeSet() } coAnswers {
            if (attempts++ == 0) throw IllegalStateException("PIN status unavailable")
            true
        }
        val vm = viewModel()
        vm.skipClicked()
        vm.errorDialogStateLiveData.value!!.peekContent().positiveClick()
        runCurrent()
        assertEquals(true, vm.skipButtonEnableLiveData.value)
        words.forEach(vm::addWordToConfirmMnemonic)
        vm.nextButtonClicked()
        runCurrent()
        coVerify(exactly = 1) { interactor.createAccount(match { !it.isBackedUp }) }
        coVerify(exactly = 1) { interactor.updateWalletBackedUp(47L) }
        verify(exactly = 1) { router.openMain() }
        assertTrue(vm.skipVisible)
    }
}
