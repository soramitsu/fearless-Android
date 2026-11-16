package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
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
class BackupMnemonicFlowTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val backupService = mockk<BackupService>(relaxed = true)
    private val cryptoTypeChooser = mockk<CryptoTypeChooserMixin>(relaxed = true)
    private val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
    private val viewModels = mutableListOf<BackupMnemonicViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { cryptoTypeChooser.selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("sr25519", CryptoType.SR25519))
        // Synthetic words for a mocked interactor, never a wallet phrase.
        coEvery { interactor.generateMnemonic(any()) } returns listOf("synthetic", "words", "only")
        coEvery { interactor.isCodeSet() } returns false
        every { backupService.isAvailable } returns true
    }

    @After
    fun tearDown() = runTest(dispatcher) {
        val jobs = viewModels.mapNotNull { it.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        jobs.joinAll()
        Dispatchers.resetMain()
    }

    private suspend fun shown(types: List<WalletEcosystem>, walletId: Long? = null) = BackupMnemonicViewModel(
        interactor, router, cryptoTypeChooser,
        SavedStateHandle(
            mapOf(BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(false, "Fresh", walletId, types))
        ),
        backupService, VmTestFixtures.resourceManager()
    ).also { viewModels += it }.also { it.mnemonic.first { words -> words.isNotEmpty() } }

    @Test
    fun `the screen shows only the controls its account types need`() = runTest(dispatcher) {
        val both = shown(listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum))
        assertTrue(both.isShowAdvancedBlock)
        assertTrue(both.isShowBackupWithGoogle)
        assertFalse(both.isShowSkipButton)
        assertTrue(both.isSubstrateAndEthereumAccount)

        val ton = shown(listOf(WalletEcosystem.Ton))
        assertTrue(ton.isShowSkipButton)
        assertFalse(ton.isShowAdvancedBlock)
        assertFalse(ton.isShowBackupWithGoogle)

        val added = shown(listOf(WalletEcosystem.Ethereum), walletId = 4)
        assertFalse("an added account has no google backup of its own", added.isShowBackupWithGoogle)
        assertEquals(4L, added.walletId)
    }

    @Test
    fun `next opens the confirmation with exactly the shown words and the typed paths`() = runTest(dispatcher) {
        val vm = shown(listOf(WalletEcosystem.Substrate, WalletEcosystem.Ethereum))
        vm.onSubstrateDerivationPathChange("//hard")
        vm.onEthereumDerivationPathChange("")
        runCurrent()
        assertEquals("//hard", vm.state.value.substrateDerivationPath)
        assertEquals(listOf("synthetic", "words", "only"), vm.state.value.mnemonicWords.map { it.word })

        vm.onNextClick(launcher)
        runCurrent()

        val payload = slot<ConfirmMnemonicPayload>()
        verify { router.openConfirmMnemonicOnCreate(capture(payload)) }
        assertEquals(listOf("synthetic", "words", "only"), payload.captured.mnemonic)
        assertNull(payload.captured.metaId)
        val extras = payload.captured.createExtras!!
        assertEquals("Fresh", extras.accountName)
        assertEquals(CryptoType.SR25519, extras.cryptoType)
        assertEquals("//hard", extras.substrateDerivationPath)
        assertEquals("//44//60//0/0/0", extras.ethereumDerivationPath)
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test
    fun `the explicit-path next refuses an invalid substrate path`() = runTest(dispatcher) {
        val vm = shown(listOf(WalletEcosystem.Substrate))
        vm.onNextClick("no-slash", "", launcher)
        runCurrent()

        assertEquals(
            "s${R.string.common_error_general_title}" to "s${R.string.common_invalid_hard_soft_numeric_password_message}",
            vm.errorWithTitleLiveData.value!!.getContentIfNotHandled()
        )
        verify(exactly = 0) { router.openConfirmMnemonicOnCreate(any()) }

        vm.onNextClick("//ok", "m/44'/60'/0'/0/7", launcher)
        runCurrent()
        val payload = slot<ConfirmMnemonicPayload>()
        verify { router.openConfirmMnemonicOnCreate(capture(payload)) }
        assertEquals("m/44'/60'/0'/0/7", payload.captured.createExtras!!.ethereumDerivationPath)
    }

    @Test
    fun `skipping the ton backup warns and confirming creates the unbacked ton wallet`() = runTest(dispatcher) {
        coEvery { interactor.createAccount(any()) } returns Result.success(9L)
        val vm = shown(listOf(WalletEcosystem.Ton))
        vm.skipClicked()

        val dialog = vm.errorDialogStateLiveData.value!!.getContentIfNotHandled()!!
        assertEquals("s${R.string.backup_not_backed_up_title}", dialog.title)
        assertEquals("s${R.string.backup_not_backed_up_confirm}", dialog.positiveButtonText)
        assertEquals(LinearLayout.HORIZONTAL, dialog.buttonsOrientation)
        coVerify(exactly = 0) { interactor.createAccount(any()) }

        dialog.positiveClick()
        runCurrent()

        val created = slot<AddAccountPayload>()
        coVerify { interactor.createAccount(capture(created)) }
        val ton = created.captured as AddAccountPayload.Ton
        assertEquals("synthetic words only", ton.mnemonic)
        assertFalse(ton.isBackedUp)
        verify { router.openCreatePincode() }
    }

    @Test
    fun `a failed skip creation is shown and nothing opens`() = runTest(dispatcher) {
        coEvery { interactor.createAccount(any()) } returns Result.failure(IllegalStateException("storage locked"))
        val vm = shown(listOf(WalletEcosystem.Substrate))
        vm.skipClicked()
        vm.errorDialogStateLiveData.value!!.getContentIfNotHandled()!!.positiveClick()
        runCurrent()

        assertEquals("storage locked", vm.errorLiveData.value!!.getContentIfNotHandled())
        val created = slot<AddAccountPayload>()
        coVerify { interactor.createAccount(capture(created)) }
        assertTrue((created.captured as AddAccountPayload.SubstrateOrEvm).freshlyGenerated)
        verify(exactly = 0) { router.openCreatePincode() }
        verify(exactly = 0) { router.openMain() }
    }

    @Test
    fun `home, info and back`() = runTest(dispatcher) {
        coEvery { interactor.isCodeSet() } returns true
        val vm = shown(listOf(WalletEcosystem.Substrate))
        vm.homeButtonClicked()
        vm.infoClicked()
        vm.onBackClick()

        verify { router.backToCreateAccountScreen() }
        verify { router.back() }
        assertEquals(Unit, vm.showInfoEvent.value!!.getContentIfNotHandled())
    }
}
