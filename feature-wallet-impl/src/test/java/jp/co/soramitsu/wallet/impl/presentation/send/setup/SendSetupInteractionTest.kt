package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.AddressInputWithScore
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.QrContentSora
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SendSetupInteractionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()

    private var config: jp.co.soramitsu.core.models.Asset = WalletTestModels.asset(priceId = "dot")
    private var chain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain = WalletTestModels.chain(assets = listOf(config))
    private var asset: jp.co.soramitsu.wallet.impl.domain.model.Asset = WalletTestModels.fundedAsset(config)
    private var phishing: PhishingModel? = null
    private var score: NomisScoreData? = null
    private var validation: Result<TransferValidationResult> = Result.success(TransferValidationResult.Valid)
    private var depositValidation: Result<TransferValidationResult> = Result.success(TransferValidationResult.Valid)
    private val shared = SendSharedState()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "res:" + firstArg<Int>() }
        every { getString(any(), *anyVararg()) } answers { "res:" + firstArg<Int>() }
    }
    private val icons = mockk<AddressIconGenerator>(relaxed = true) {
        coEvery { createWalletIcon(any(), any()) } returns PictureDrawable(Picture())
    }
    private val scores = mockk<NomisScoreInteractor>(relaxed = true) {
        coEvery { getNomisScore(any()) } answers { score }
        every { getNomisScoreFromMemoryCache(any()) } returns null
    }
    private val wallet = mockk<WalletInteractor>(relaxed = true) {
        coEvery { getChain(any()) } answers { chain }
        coEvery { getCurrentAsset(any(), any()) } answers { asset }
        coEvery { getSelectedMetaAccount() } returns WalletTestModels.metaAccount()
        every { assetFlow(any(), any()) } answers { flowOf(asset) }
        every { getChains() } answers { flowOf(listOf(chain)) }
        coEvery { validateSendAddress(any(), any()) } answers { secondArg<String>().isNotBlank() }
        coEvery { observeTransferFee(any()) } returns flowOf(BigDecimal("0.25"))
        coEvery { getPhishingInfo(any()) } answers { phishing }
        coEvery { tryReadSoraFormat(any()) } returns null
        coEvery { extractTonAddress(any()) } returns null
    }
    private val validator = mockk<ValidateTransferUseCase> {
        coEvery { validateTransfer(any(), any(), any(), any(), any(), any(), any(), any()) } answers { validation }
        coEvery { validateExistentialDeposit(any(), any(), any(), any(), any(), any(), any(), any()) } answers { depositValidation }
    }
    private val constants = mockk<WalletConstants> { coEvery { tip(any()) } returns BigInteger.ONE }
    private val address = mockk<CurrentAccountAddressUseCase>().also { useCase -> coEvery { useCase(any()) } returns WalletTestModels.ALICE }
    private val quick = mockk<QuickInputsUseCase> {
        coEvery { calculateTransfersQuickInputs(any(), any()) } returns mapOf(
            0.5 to BigDecimal("40.1234567"), 1.0 to BigDecimal("99.75")
        )
    }
    private val models = mutableListOf<SendSetupViewModel>()

    @After
    fun dispose() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        models.forEach { it.viewModelScope.cancel() }
        await { jobs.all { it.isCompleted } }
    }

    @Test
    fun loadedAssetShowsFeeAndAmountAndRoutesInputActions() {
        val vm = model()
        await { vm.state.value.feeInfoState.feeAmount != null }
        assertEquals("0.25 DOT", vm.state.value.feeInfoState.feeAmount)
        assertEquals("usd1.50", vm.state.value.feeInfoState.feeAmountFiat)
        assertTrue(vm.state.value.sendAllAllowed)
        assertFalse(vm.state.value.buttonState.enabled)

        vm.onAmountInput(BigDecimal("2"))
        vm.onAmountFocusChanged(true)
        vm.setSoftKeyboardOpen(true)
        await { vm.state.value.amountInputState.isFocused && vm.state.value.isSoftKeyboardOpen }
        assertEquals(BigDecimal("2"), vm.state.value.amountInputState.tokenAmount)
        assertTrue(vm.state.value.buttonState.enabled)

        vm.onChainClick()
        vm.onTokenClick()
        vm.onHistoryClick()
        vm.onQrClick()
        vm.onNavigationClick()
        assertNotNull(vm.openScannerEvent.value?.peekContent())
        verify { router.openSelectChain(assetId = config.id, chainId = chain.id, chooserMode = false) }
        verify { router.openSelectAsset(selectedAssetId = config.id, selectedChainId = chain.id) }
        verify { router.openAddressHistory(chain.id) }
        verify { router.back() }
    }

    @Test
    fun clipboardAndClearUpdateTheRecipient() {
        every { clipboard.getFromClipboard() } returns "  " + WalletTestModels.BOB + "  "
        val vm = model()
        vm.onPasteClick()
        await { vm.state.value.addressInputState is AddressInputWithScore.Filled }
        assertTrue((vm.state.value.addressInputState as AddressInputWithScore.Filled).address.startsWith("5FH"))
        vm.onAddressInputClear()
        await { vm.state.value.addressInputState is AddressInputWithScore.Empty }
        assertTrue(vm.state.value.addressInputState is AddressInputWithScore.Empty)
        vm.onAmountInput(null)
        await { !vm.state.value.buttonState.enabled }
        assertEquals(BigDecimal.ZERO, vm.state.value.amountInputState.tokenAmount)
    }

    @Test
    fun successfulValidationOpensAnExactReviewedDraft() {
        val draft = slot<TransferDraft>()
        every { router.openSendConfirm(capture(draft), any(), any(), any(), any()) } returns Unit
        val vm = readyToSend()
        vm.onNextClick()
        await { draft.isCaptured }
        assertEquals(AssetPayload(config.chainId, config.id), draft.captured.assetPayload)
        assertEquals(BigDecimal("2"), draft.captured.amount)
        assertEquals(WalletTestModels.BOB, draft.captured.recipientAddress)
        assertEquals(BigDecimal("0.25"), draft.captured.fee)
        assertEquals(1L, draft.captured.walletId)
        assertFalse(draft.captured.sendMax)
        coVerify { validator.validateTransfer(
            BigInteger("20000000000"), asset, WalletTestModels.BOB, WalletTestModels.ALICE,
            BigInteger("2500000000"), any(), false, false
        ) }
    }

    @Test
    fun failedValidationRestoresContinueAndShowsTheFailure() {
        validation = Result.failure(IllegalStateException("balance lookup failed"))
        val vm = readyToSend()
        vm.onNextClick()
        await { vm.errorLiveData.value != null }
        assertEquals("balance lookup failed", vm.errorLiveData.value!!.peekContent())
        assertFalse(vm.state.value.buttonState.loading)
        assertTrue(vm.state.value.buttonState.enabled)
        verify(exactly = 0) { router.openSendConfirm(any(), any(), any(), any(), any()) }
    }

    @Test
    fun invalidAddressValidationShowsItsExplanationWithoutOpeningConfirmation() {
        validation = Result.success(TransferValidationResult.InvalidAddress)
        val vm = readyToSend()
        vm.onNextClick()
        await { vm.errorWithTitleLiveData.value != null }
        assertEquals("res:" + R.string.transfer_address_not_valid_error_message, vm.errorWithTitleLiveData.value!!.peekContent().second)
        verify(exactly = 0) { router.openSendConfirm(any(), any(), any(), any(), any()) }
    }

    @Test
    fun validationWarningWaitsForConfirmation() {
        val warning = TransferValidationResult.ExistentialDepositUnknown("DOT")
        validation = Result.success(warning)
        val vm = readyToSend()
        vm.onNextClick()
        await { vm.openValidationWarningEvent.value != null }
        assertEquals(warning, vm.openValidationWarningEvent.value!!.peekContent().first)
        verify(exactly = 0) { router.openSendConfirm(any(), any(), any(), any(), any()) }
        vm.warningConfirmed(warning)
        await { runCatching { verify { router.openSendConfirm(any(), any(), any(), any(), false) } }.isSuccess }
    }

    @Test
    fun depositWarningCanBeCancelledOrAcceptedForSendAll() {
        val warning = TransferValidationResult.ExistentialDepositWarning("1 DOT")
        depositValidation = Result.success(warning)
        val vm = readyToSend()
        await { vm.openValidationWarningEvent.value != null }
        assertEquals(warning, vm.openValidationWarningEvent.value!!.peekContent().first)
        vm.warningCancelled(warning)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal.ZERO }
        assertFalse(vm.state.value.sendAllChecked)

        vm.warningConfirmed(warning)
        await { vm.state.value.sendAllChecked }
        assertTrue(vm.state.value.sendAllChecked)
        vm.onSendAllChecked(false)
        await { !vm.state.value.sendAllChecked }
        assertEquals(BigDecimal.ZERO, vm.state.value.amountInputState.tokenAmount)
    }

    @Test
    fun quickAmountUsesTheCalculatedBalanceAndSendAllCanBeCleared() {
        val vm = model()
        await { vm.state.value.feeInfoState.feeAmount != null }
        vm.onQuickAmountInput(0.5)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal("40.12345") }
        assertEquals(BigDecimal("40.12345"), vm.state.value.amountInputState.tokenAmount)
        vm.onSendAllChecked(true)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal("99.75") }
        assertTrue(vm.state.value.sendAllChecked)
        vm.onSendAllChecked(false)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal.ZERO }
        assertEquals(BigDecimal.ZERO, vm.state.value.amountInputState.tokenAmount)
    }

    @Test
    fun everyPhishingTypeShowsItsReasonAndCanExpand() {
        val vm = model()
        for (type in PhishingType.entries) {
            phishing = PhishingModel(WalletTestModels.BOB, "Reported recipient", type, "Report category")
            vm.onAddressInput(WalletTestModels.BOB + type.name)
            await { vm.state.value.warningInfoState?.extras?.any { it.second == type.capitalizedName } == true }
            assertEquals(3, vm.state.value.warningInfoState!!.extras.size)
            assertTrue(vm.state.value.warningInfoState!!.message.startsWith("res:"))
        }
        vm.onWarningInfoClick()
        await { vm.state.value.warningInfoState?.isExpanded == true }
        assertTrue(vm.state.value.warningInfoState!!.isExpanded)
    }

    @Test
    fun lowNomisScoreShowsTheScoreWarning() {
        score = NomisScoreData(1, 10, 1, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1, 0, 0.0, 0.0, 0.0, null)
        val vm = model()
        vm.onAddressInput(WalletTestModels.BOB)
        await { vm.state.value.warningInfoState != null }
        assertEquals("res:" + R.string.scam_description_lowscore_text, vm.state.value.warningInfoState!!.message)
        assertEquals(10, (vm.state.value.addressInputState as AddressInputWithScore.Filled).score)
    }

    @Test
    fun ordinaryQrAddressUnlocksLockedInputAndInvalidQrExplainsFailure() {
        val vm = model(mapOf(SendSetupFragment.KEY_LOCK_AMOUNT to true, SendSetupFragment.KEY_INITIAL_AMOUNT to BigDecimal.ONE))
        vm.qrCodeScanned(WalletTestModels.BOB)
        await { vm.state.value.addressInputState is AddressInputWithScore.Filled && !vm.state.value.isInputLocked }
        assertTrue(vm.state.value.amountInputState.inputEnabled)
        vm.qrCodeScanned("not an address")
        await { vm.errorDialogStateLiveData.value != null }
        assertEquals("res:" + R.string.error_invalid_address, vm.errorDialogStateLiveData.value!!.peekContent().message)
    }

    @Test
    fun soraQrSelectsItsAssetAndLocksItsRequestedAmount() {
        // The view model looks for the build's SORA network: the test network in debug builds, mainnet in release.
        val soraChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        config = config.copy(chainId = soraChainId, currencyId = "sora-token")
        chain = WalletTestModels.chain(id = soraChainId, assets = listOf(config))
        asset = WalletTestModels.fundedAsset(config)
        coEvery { wallet.tryReadSoraFormat("sora-qr") } returns QrContentSora(WalletTestModels.BOB, "", "Recipient", "sora-token", "3.5")
        val vm = model()
        vm.qrCodeScanned("sora-qr")
        await { vm.state.value.isInputLocked }
        main.dispatcher.scheduler.advanceTimeBy(301)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal("3.5") }
        assertEquals(soraChainId, shared.chainId)
        assertEquals(config.id, shared.assetId)
        assertFalse(vm.state.value.amountInputState.inputEnabled)
    }

    @Test
    fun tonSupportsCommentsAndAcceptsAValidQrAddress() {
        config = config.copy(chainId = "ton")
        chain = WalletTestModels.chain(id = "ton", assets = listOf(config), ecosystem = Ecosystem.Ton)
        asset = WalletTestModels.fundedAsset(config)
        coEvery { wallet.getSelectedMetaAccount() } returns WalletTestModels.metaAccount(ton = ByteArray(32) { 1 })
        val vm = model()
        await { vm.state.value.commentState != null }
        vm.onCommentInput("Invoice 42")
        vm.qrCodeScanned(WalletTestModels.TON_RAW)
        await { vm.state.value.addressInputState is AddressInputWithScore.Filled }
        assertEquals("Invoice 42", vm.state.value.commentState!!.text)
        assertFalse(vm.state.value.isInputLocked)
    }

    @Test
    fun initialRecipientChoosesItsSingleSupportedChain() {
        val vm = model(mapOf(SendSetupFragment.KEY_INITIAL_ADDRESS to WalletTestModels.BOB), withPayload = false)
        await { shared.chainId != null }
        assertEquals(config.chainId, shared.chainId)
        assertEquals(config.id, shared.assetId)
        assertTrue(vm.state.value.addressInputState is AddressInputWithScore.Filled)
    }

    @Test
    fun initialRecipientWithMultipleAssetsOpensTheAssetPicker() {
        chain = chain.copy(assets = listOf(config, config.copy(id = "other")))
        model(mapOf(SendSetupFragment.KEY_INITIAL_ADDRESS to WalletTestModels.BOB), withPayload = false)
        await {
            main.dispatcher.scheduler.runCurrent()
            runCatching { verify { router.openSelectChainAsset(chain.id) } }.isSuccess
        }
        verify(exactly = 1) { router.openSelectChainAsset(chain.id) }
    }

    @Test
    fun unsupportedInitialRecipientOffersToLeaveTheSendScreen() {
        val vm = model(mapOf(SendSetupFragment.KEY_INITIAL_ADDRESS to "invalid"), withPayload = false)
        await { vm.errorDialogStateLiveData.value != null }
        vm.errorDialogStateLiveData.value!!.peekContent().negativeClick()
        verify { router.back() }
        assertNull(shared.chainId)
    }

    private fun readyToSend(): SendSetupViewModel {
        val vm = model()
        vm.onAddressInput(WalletTestModels.BOB)
        vm.onAmountInput(BigDecimal("2"))
        await { vm.state.value.feeInfoState.feeAmount != null && vm.state.value.buttonState.enabled }
        return vm
    }

    private fun model(arguments: Map<String, Any?> = emptyMap(), withPayload: Boolean = true): SendSetupViewModel {
        val values = if (withPayload) mapOf(SendSetupFragment.KEY_PAYLOAD to AssetPayload(config.chainId, config.id)) + arguments else arguments
        return SendSetupViewModel(
            shared, SavedStateHandle(values), resources, wallet, scores, mockk(relaxed = true),
            object : ExistentialDepositUseCase {
                override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset) = Result.success(BigInteger.ONE)
            },
            constants, router, clipboard, icons, address, validator, quick
        ).also { models += it }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            main.dispatcher.scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
        assertTrue("View-model condition did not complete", condition())
    }
}
