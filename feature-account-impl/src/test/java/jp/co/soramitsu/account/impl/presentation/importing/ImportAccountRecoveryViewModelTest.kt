package jp.co.soramitsu.account.impl.presentation.importing

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyRecovery
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryIdentityMismatch
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.common.mixin.api.CryptoTypeChooserMixin
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
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

@OptIn(ExperimentalCoroutinesApi::class)
class ImportAccountRecoveryViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<AccountInteractor>(relaxed = true)
    private val router = mockk<AccountRouter>(relaxed = true)
    private val recovery = mockk<WalletKeyRecovery>()
    private val pinRecovery = mockk<jp.co.soramitsu.account.impl.domain.recovery.WalletPinRecovery>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>()
    private val models = mutableListOf<ImportAccountViewModel>()
    private val saved = SavedStateHandle(mapOf(
        ImportAccountFragment.IMPORT_REBIND_KEY to RecoveryTarget.from(7, WalletSecretScope.Ethereum),
        ImportAccountFragment.IMPORT_ACCOUNT_TYPE_KEY to WalletEcosystem.Ethereum,
        ImportAccountFragment.IMPORT_MODE_KEY to ImportMode.MnemonicPhrase
    ))

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun matchingRecoveryRebindsTheExistingIdAndNeverCallsCreateOrAddAccount() = runTest(dispatcher) {
        coEvery { recovery.restore(7, WalletSecretScope.Ethereum, ImportMode.MnemonicPhrase, PHRASE, "m/44/60/0", "") } returns WalletSecretRebindService.Result.RESTORED
        val model = model()
        (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = PHRASE
        model.ethereumDerivationPathLiveData.value = "m/44/60/0"
        model.nextClicked()
        runCurrent()
        coVerify(exactly = 1) { recovery.restore(7, WalletSecretScope.Ethereum, ImportMode.MnemonicPhrase, PHRASE, "m/44/60/0", "") }
        coVerify(exactly = 0) { interactor.createAccount(any()) }
        verify(exactly = 0) { router.openCreatePincode() }
        verify(exactly = 1) { router.back() }
        assertEquals("", (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value)
        assertEquals("", model.ethereumDerivationPathLiveData.value)
        assertNull(model.recoveryError.value)
        assertTrue(saved.keys().all { it in setOf(ImportAccountFragment.IMPORT_REBIND_KEY,
            ImportAccountFragment.IMPORT_ACCOUNT_TYPE_KEY, ImportAccountFragment.IMPORT_MODE_KEY) })
    }

    @Test fun mismatchStaysInlineAndDoesNotRouteAwayOrTryAnOrdinaryImport() = runTest(dispatcher) {
        coEvery { recovery.restore(any(), any(), any(), any(), any(), any()) } throws WalletRecoveryIdentityMismatch()
        val model = model()
        (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = PHRASE
        model.nextClicked()
        runCurrent()
        assertEquals("text-${R.string.wallet_recovery_different_key}", model.recoveryError.value)
        verify(exactly = 0) { router.back() }
        coVerify(exactly = 0) { interactor.createAccount(any()) }
    }

    @Test fun leavingTheScreenCancelsPendingRecoveryAndClearsEveryInputMode() = runTest(dispatcher) {
        coEvery { recovery.restore(any(), any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        val model = model()
        model.sourceTypes.filterIsInstance<MnemonicImportSource>().single().mnemonicContentLiveData.value = PHRASE
        model.sourceTypes.filterIsInstance<RawSeedImportSource>().single().rawSeedLiveData.value = "private-key"
        val json = model.sourceTypes.filterIsInstance<JsonImportSource>().single()
        json.jsonContentLiveData.value = "encrypted-json"
        json.passwordLiveData.value = "private-password"
        model.nextClicked()
        runCurrent()
        model.recoveryScreenStopped()
        runCurrent()
        assertEquals("", json.jsonContentLiveData.value)
        assertEquals("", json.passwordLiveData.value)
        assertEquals("", model.sourceTypes.filterIsInstance<MnemonicImportSource>().single().mnemonicContentLiveData.value)
        assertEquals("", model.sourceTypes.filterIsInstance<RawSeedImportSource>().single().rawSeedLiveData.value)
        assertNull(model.recoveryError.value)
        verify(exactly = 0) { router.back() }
    }

    @Test fun recoveryJsonUsesTheDurableSigningTypeAndAllowsAnEmptyBackupPassword() = runTest(dispatcher) {
        val json = "{\"encoded\":\"test-backup\"}"
        every { clipboard.getFromClipboard() } returns json
        val source = model().sourceTypes.filterIsInstance<JsonImportSource>().single()
        source.blockchainTypeFlow.value = WalletEcosystem.Substrate
        source.pasteClicked()
        assertEquals(json, source.jsonContentLiveData.value)
        assertTrue(source.isFieldsValid())
        coVerify(exactly = 0) { interactor.processAccountJson(any()) }
        source.clearRecoveryInput()
        assertEquals("", source.jsonContentLiveData.value)
    }

    @Test fun pinProofRouteVerifiesTheGrantAndNeverUsesTheOrdinaryImportOrRebindShortcut() = runTest(dispatcher) {
        saved[ImportAccountFragment.PIN_RECOVERY_CHALLENGE] = "public-in-memory-challenge-id"
        val model = model()
        (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = PHRASE
        model.nextClicked()
        runCurrent()
        coVerify(exactly = 1) { pinRecovery.verifyWallet("public-in-memory-challenge-id", RecoveryTarget(7, "ethereum"),
            ImportMode.MnemonicPhrase, PHRASE, "", "") }
        coVerify(exactly = 0) { recovery.restore(any(), any(), any(), any(), any(), any()); interactor.createAccount(any()) }
        assertEquals("", (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value)
        verify(exactly = 1) { router.back() }
    }

    @Test fun expiredPinProofStaysOnTheInputScreenAndDoesNotRoutePastThePin() = runTest(dispatcher) {
        saved[ImportAccountFragment.PIN_RECOVERY_CHALLENGE] = "expired-challenge"
        coEvery { pinRecovery.verifyWallet(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("expired")
        val model = model()
        (model.selectedSourceLiveData.value as MnemonicImportSource).mnemonicContentLiveData.value = PHRASE
        model.nextClicked()
        runCurrent()
        assertEquals("text-${R.string.wallet_recovery_restore_failed}", model.recoveryError.value)
        verify(exactly = 0) { router.back() }
        coVerify(exactly = 0) { recovery.restore(any(), any(), any(), any(), any(), any()) }
    }

    private fun model(): ImportAccountViewModel {
        val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
        val crypto = mockk<CryptoTypeChooserMixin> {
            every { selectedEncryptionTypeLiveData } returns MutableLiveData(CryptoTypeModel("SR25519", CryptoType.SR25519))
        }
        return ImportAccountViewModel(interactor, router, resources, crypto, clipboard, mockk(), recovery, pinRecovery, saved).also(models::add)
    }

    private companion object { const val PHRASE = "test test test test test test test test test test test junk" }
}
