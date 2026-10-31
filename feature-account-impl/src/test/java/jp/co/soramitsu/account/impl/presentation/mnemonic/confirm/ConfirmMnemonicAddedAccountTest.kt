package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** An account added to an existing wallet: its phrase backs up that account, not the wallet's own keys. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmMnemonicAddedAccountTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val viewModels = mutableListOf<ConfirmMnemonicViewModel>()
    private val buttonObserver = Observer<Boolean> { }
    private val walletId = 50L
    // Synthetic input for a mocked account interactor, never a wallet seed.
    private val words = listOf("first", "second", "third")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.isCodeSet() } returns true
        coEvery { interactor.createAccount(any()) } returns Result.success(walletId)
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

    private fun wallet(backedUp: Boolean): MetaAccount = mockk { every { isBackedUp } returns backedUp }

    private fun viewModel(): ConfirmMnemonicViewModel {
        val payload = ConfirmMnemonicPayload(
            mnemonic = words,
            metaId = walletId,
            createExtras = ConfirmMnemonicPayload.CreateExtras("Added account", CryptoType.SR25519, "", "m/44'/60'/0'/0/0"),
            accountTypes = listOf(WalletEcosystem.Substrate)
        )
        return ConfirmMnemonicViewModel(
            mockk(relaxed = true), interactor, router, mockk(relaxed = true),
            SavedStateHandle(mapOf(ConfirmMnemonicFragment.KEY_PAYLOAD to payload))
        ).also {
            viewModels += it
            it.nextButtonEnableLiveData.observeForever(buttonObserver)
            it.skipButtonEnableLiveData.observeForever(buttonObserver)
        }
    }

    private fun addedAccount(isBackedUp: Boolean) =
        AddAccountPayload.AdditionalEvm(walletId, "Added account", words.joinToString(" "), "m/44'/60'/0'/0/0", isBackedUp)

    private fun ConfirmMnemonicViewModel.confirmPhrase() {
        words.forEach(::addWordToConfirmMnemonic)
        nextButtonClicked()
    }

    private fun ConfirmMnemonicViewModel.skipPhrase() {
        skipClicked()
        errorDialogStateLiveData.value!!.peekContent().positiveClick()
    }

    @Test
    fun confirmingAnAddedAccountLeavesAnUnbackedWalletWarned() = runTest(dispatcher) {
        // For example a TON wallet whose phrase was skipped, now given an account with a new phrase.
        coEvery { interactor.getMetaAccount(walletId) } returns wallet(backedUp = false)
        val vm = viewModel()

        vm.confirmPhrase()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(addedAccount(isBackedUp = false)) }
        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun confirmingAnAddedAccountKeepsABackedUpWalletBackedUp() = runTest(dispatcher) {
        coEvery { interactor.getMetaAccount(walletId) } returns wallet(backedUp = true)
        val vm = viewModel()

        vm.confirmPhrase()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(addedAccount(isBackedUp = true)) }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun confirmingAfterASkippedAttemptCannotMarkAnUnbackedWallet() = runTest(dispatcher) {
        coEvery { interactor.getMetaAccount(walletId) } returns wallet(backedUp = false)
        var pinReads = 0
        coEvery { interactor.isCodeSet() } coAnswers {
            if (pinReads++ == 0) throw IllegalStateException("PIN status unavailable")
            true
        }
        val vm = viewModel()
        vm.skipPhrase()
        runCurrent()

        vm.confirmPhrase()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(addedAccount(isBackedUp = false)) }
        coVerify(exactly = 0) { interactor.updateWalletBackedUp(any()) }
        verify(exactly = 1) { router.openMain() }
    }

    @Test
    fun confirmingAfterASkippedAttemptStillMarksAWalletThatWasBackedUp() = runTest(dispatcher) {
        // The skipped attempt itself stored the wallet as not backed up.
        coEvery { interactor.getMetaAccount(walletId) } returnsMany listOf(wallet(backedUp = true), wallet(backedUp = false))
        var pinReads = 0
        coEvery { interactor.isCodeSet() } coAnswers {
            if (pinReads++ == 0) throw IllegalStateException("PIN status unavailable")
            true
        }
        val vm = viewModel()
        vm.skipPhrase()
        runCurrent()

        vm.confirmPhrase()
        runCurrent()

        coVerify(exactly = 1) { interactor.createAccount(addedAccount(isBackedUp = false)) }
        coVerify(exactly = 1) { interactor.updateWalletBackedUp(walletId) }
        verify(exactly = 1) { router.openMain() }
    }
}
