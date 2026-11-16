package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Intent
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.exceptions.Bip39Exception
import jp.co.soramitsu.feature_account_impl.R
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The non-recovery import flows: which secret reaches which interactor call, and how failures are told. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportAccountViewModelFlowTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val fileReader = mockk<FileReader>(relaxed = true)
    private val models = mutableListOf<ImportAccountViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { interactor.isCodeSet() } returns false
        coEvery { interactor.createAccount(any()) } returns Result.success(1L)
        coEvery { interactor.importFromSeed(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { interactor.importFromJson(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After fun tearDown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(ecosystem: WalletEcosystem, mode: ImportMode, walletId: Long? = null): ImportAccountViewModel {
        val crypto = mockk<CryptoTypeChooserMixin> {
            every { selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("ED25519", CryptoType.ED25519))
        }
        val saved = SavedStateHandle(
            buildMap<String, Any> {
                put(ImportAccountFragment.IMPORT_ACCOUNT_TYPE_KEY, ecosystem)
                put(ImportAccountFragment.IMPORT_MODE_KEY, mode)
                walletId?.let { put(ImportAccountFragment.IMPORT_WALLET_ID_KEY, it) }
            }
        )
        return ImportAccountViewModel(
            interactor, router, VmTestFixtures.resourceManager(), crypto, mockk(), fileReader, mockk(), mockk(relaxed = true), saved
        ).also { vm ->
            models += vm
            vm.nextButtonState.observeForever { }
            vm.nameLiveData.value = "Imported"
        }
    }

    @Test
    fun `the initial mode picks its source and the chooser offers all three`() {
        assertTrue(model(WalletEcosystem.Substrate, ImportMode.RawSeed).selectedSourceLiveData.value is RawSeedImportSource)
        assertTrue(model(WalletEcosystem.Substrate, ImportMode.Json).selectedSourceLiveData.value is JsonImportSource)
        assertTrue(model(WalletEcosystem.Substrate, ImportMode.Google).selectedSourceLiveData.value is MnemonicImportSource)
        assertTrue(model(WalletEcosystem.Substrate, ImportMode.Preinstalled).selectedSourceLiveData.value is MnemonicImportSource)

        val vm = model(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase)
        vm.openSourceChooserClicked()
        val payload = vm.showSourceSelectorChooserLiveData.value!!.getContentIfNotHandled()!!
        assertEquals(3, payload.data.size)
        assertTrue(payload.selected is MnemonicImportSource)

        vm.sourceTypeChanged(vm.sourceTypes[1])
        assertTrue(vm.selectedSourceLiveData.value is RawSeedImportSource)
        vm.homeButtonClicked()
        verify { router.backToWelcomeScreen() }
        vm.recoveryScreenStopped()
        assertEquals("Imported", vm.nameLiveData.value)
    }

    @Test
    fun `next needs valid source fields and a name`() {
        val vm = model(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase)
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)
        (vm.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = WORDS
        assertEquals(ButtonState.NORMAL, vm.nextButtonState.value)
        vm.nameLiveData.value = ""
        assertEquals(ButtonState.DISABLED, vm.nextButtonState.value)
    }

    @Test
    fun `an invalid substrate path is refused before anything is imported`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase)
        (vm.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = WORDS
        vm.substrateDerivationPathLiveData.value = "no-slash"
        vm.nextClicked()
        runCurrent()

        val (title, message) = vm.errorWithTitleLiveData.value!!.getContentIfNotHandled()!!
        assertEquals("s${R.string.common_error_general_title}", title)
        assertEquals("s${R.string.common_invalid_hard_soft_numeric_password_message}", message)
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test
    fun `a substrate seed asks about ethereum and declining imports the substrate seed alone`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.RawSeed)
        (vm.selectedSourceLiveData.value as RawSeedImportSource).rawSeedLiveData.value = SUBSTRATE_SEED
        vm.substrateDerivationPathLiveData.value = "//hard"
        vm.nextClicked()
        assertNotNull(vm.showEthAccountsDialog.value!!.getContentIfNotHandled())

        vm.onAddEthAccountDeclined()
        runCurrent()

        coVerify { interactor.importFromSeed(null, SUBSTRATE_SEED, "Imported", "//hard", CryptoType.ED25519, null, null) }
        verify { router.openCreatePincode() }
    }

    @Test
    fun `accepting ethereum clears the field and imports both seeds together`() = runTest(dispatcher) {
        coEvery { interactor.isCodeSet() } returns true
        val vm = model(WalletEcosystem.Substrate, ImportMode.RawSeed)
        val source = vm.selectedSourceLiveData.value as RawSeedImportSource
        source.rawSeedLiveData.value = SUBSTRATE_SEED
        vm.nextClicked()
        vm.onAddEthAccountConfirmed()

        assertEquals(WalletEcosystem.Ethereum, vm.blockchainLiveData.value)
        assertEquals("", source.rawSeedLiveData.value)
        source.rawSeedLiveData.value = ETHEREUM_SEED
        vm.nextClicked()
        runCurrent()

        coVerify { interactor.importFromSeed(null, SUBSTRATE_SEED, "Imported", "", CryptoType.ED25519, ETHEREUM_SEED, null) }
        verify { router.openMain() }
    }

    @Test
    fun `a substrate json then an ethereum json are imported together with the typed password`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.Json)
        val source = vm.selectedSourceLiveData.value as JsonImportSource
        source.jsonContentLiveData.value = "{substrate}"
        source.passwordLiveData.value = "pw"
        vm.nextClicked()
        assertNotNull(vm.showEthAccountsDialog.value!!.getContentIfNotHandled())
        vm.onAddEthAccountConfirmed()
        assertEquals("", source.jsonContentLiveData.value)
        assertEquals("", source.passwordLiveData.value)

        source.jsonContentLiveData.value = "{ethereum}"
        source.passwordLiveData.value = "pw"
        vm.nextClicked()
        runCurrent()

        coVerify { interactor.importFromJson(null, "{substrate}", "pw", "Imported", "{ethereum}", null) }
    }

    @Test
    fun `a declined ethereum json imports the substrate json alone into the given wallet`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.Json, walletId = 12)
        val source = vm.selectedSourceLiveData.value as JsonImportSource
        source.jsonContentLiveData.value = "{substrate}"
        source.passwordLiveData.value = "pw"
        vm.nextClicked()
        vm.onAddEthAccountDeclined()
        runCurrent()

        coVerify { interactor.importFromJson(12, "{substrate}", "pw", "Imported", null, null) }
    }

    @Test
    fun `ton words create a ton wallet and words for an existing wallet add only its ethereum account`() = runTest(dispatcher) {
        val ton = model(WalletEcosystem.Ton, ImportMode.MnemonicPhrase)
        (ton.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = WORDS
        ton.nextClicked()
        runCurrent()
        coVerify { interactor.createAccount(match { it is AddAccountPayload.Ton && it.mnemonic == WORDS && it.accountName == "Imported" }) }

        val additional = model(WalletEcosystem.Ethereum, ImportMode.MnemonicPhrase, walletId = 5)
        (additional.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = WORDS
        additional.ethereumDerivationPathLiveData.value = "m/44'/60'/0'/0/3"
        additional.nextClicked()
        runCurrent()
        coVerify {
            interactor.createAccount(
                match { it is AddAccountPayload.AdditionalEvm && it.walletId == 5L && it.ethereumDerivationPath == "m/44'/60'/0'/0/3" }
            )
        }
    }

    @Test
    fun `import failures show the source's own explanation, the duplicate message or the general one`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.MnemonicPhrase)
        (vm.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = WORDS
        val expected = listOf(
            Bip39Exception() to ("s${R.string.import_mnemonic_invalid_title}" to "s${R.string.mnemonic_error_try_another_one}"),
            AccountAlreadyExistsException() to ("s${R.string.account_add_already_exists_message}" to "s${R.string.account_error_try_another_one}"),
            IllegalStateException() to ("s${R.string.common_error_general_title}" to "s${R.string.common_undefined_error_message}")
        )
        expected.forEach { (failure, messages) ->
            coEvery { interactor.createAccount(any()) } returns Result.failure(failure)
            vm.nextClicked()
            runCurrent()
            assertEquals(messages, vm.errorWithTitleLiveData.value!!.getContentIfNotHandled())
            assertEquals(ButtonState.NORMAL, vm.nextButtonState.value)
        }
    }

    @Test
    fun `a chosen json file is read only for the json source's own request`() = runTest(dispatcher) {
        val vm = model(WalletEcosystem.Substrate, ImportMode.Json)
        val source = vm.selectedSourceLiveData.value as JsonImportSource
        source.chooseFileClicked()
        val uri = mockk<Uri>()
        val intent = mockk<Intent> { every { data } returns uri }

        vm.systemCallResultReceived(999, intent)
        runCurrent()
        coVerify(exactly = 0) { fileReader.readFile(any()) }

        vm.systemCallResultReceived(source.chooseJsonFileEvent.value!!.peekContent(), intent)
        runCurrent()
        coVerify { fileReader.readFile(uri) }
    }

    private companion object {
        const val WORDS = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val SUBSTRATE_SEED = "0x" + "11"
        const val ETHEREUM_SEED = "0x" + "22"
    }
}
