package jp.co.soramitsu.wallet.impl.presentation.send.confirm

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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendException
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendQuoteStore
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendReview
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.CBDCTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.TonTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** The send confirmation's secondary paths; every submission is a local mock, nothing is signed or sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmSendViewModelCoverageTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    @Volatile private var validation: Result<TransferValidationResult> = Result.success(TransferValidationResult.Valid)
    @Volatile private var submission: Result<String> = Result.success(HASH)
    private val validations = AtomicInteger()
    private val performed = CopyOnWriteArrayList<Transfer>()
    private val chainAsset = mockk<ChainAsset>(relaxed = true).also {
        every { it.chainId } returns CHAIN
        every { it.id } returns "native"
        every { it.symbol } returns "LOCAL"
        every { it.precision } returns 12
        every { it.currencyId } returns "local-native"
        every { it.isUtility } returns true
        every { it.chainIcon } returns null
        every { it.iconUrl } returns ICON_URL
    }
    private val chain = mockk<Chain>(relaxed = true).also {
        every { it.id } returns CHAIN
        every { it.name } returns "Local fixture"
        every { it.ecosystem } returns Ecosystem.Substrate
        every { it.assets } returns listOf(chainAsset)
        every { it.explorers } returns emptyList()
        every { it.externalApi } returns null
        every { it.isUsesAppId } returns false
    }
    private val asset = Asset.createEmpty(chainAsset, 7, ByteArray(32), minSupportedVersion = null)
        .copy(freeInPlanksState = AssetAmountState.Known(BigInteger.TEN.pow(14)))
    private val interactor = mockk<WalletInteractor>(relaxed = true).also {
        coEvery { it.getChain(CHAIN) } returns chain
        every { it.assetFlow(CHAIN, "native") } returns MutableStateFlow(asset)
        every { it.observeAddressBook(CHAIN) } returns flowOf(emptyList())
        coEvery { it.getSelectedMetaAccount() } returns mockk<MetaAccount>(relaxed = true)
        coEvery { it.observeTransferFee(any()) } returns flowOf(BigDecimal.ONE)
        coEvery { it.performTransfer(any()) } answers {
            performed += firstArg<Transfer>()
            submission
        }
    }
    private val validator = mockk<ValidateTransferUseCase>(relaxed = true).also {
        coEvery { it.validateExistentialDeposit(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            validations.incrementAndGet()
            validation
        }
    }
    private val currentAddress = mockk<CurrentAccountAddressUseCase>().also {
        coEvery { it.invoke(CHAIN) } returns SENDER
    }
    private val polkaswap = mockk<PolkaswapInteractor>(relaxed = true)
    private val router = mockk<WalletRouter>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val externalActions = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val validity = mockk<TransferValidityChecks.Presentation>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true).also {
        every { it.getString(any()) } answers { text(firstArg()) }
        every { it.getString(any(), *anyVararg()) } answers { formatted(firstArg()) }
    }
    private val owned = mutableListOf<ConfirmSendViewModel>()
    private val uncaught = mutableListOf<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
    }

    @After fun teardown() {
        owned.forEach { vm ->
            val job = vm.viewModelScope.coroutineContext.job
            vm.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { job.join() } }
        }
        TransferSeal.clear()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals("No uncaught ViewModel failures", emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    @Test fun `a confirmation without a draft cannot open`() {
        val error = assertThrows(IllegalStateException::class.java) {
            ConfirmSendViewModel(
                interactor = interactor,
                polkaswapInteractor = polkaswap,
                router = router,
                addressIconGenerator = mockk(relaxed = true),
                chainRegistry = chainRegistry,
                externalAccountActions = externalActions,
                transferValidityChecks = validity,
                savedStateHandle = SavedStateHandle(),
                resourceManager = resources,
                currentAccountAddress = currentAddress,
                validateTransferUseCase = validator
            )
        }
        assertEquals("Required data not provided for send confirmation", error.message)
    }

    @Test fun `back leaves and copy offers the recipient on the network explorers`() {
        coEvery { chainRegistry.getChain(CHAIN) } returns chain
        val vm = viewModel()

        vm.onNavigationClick()
        vm.copyRecipientAddressClicked()

        val payload = slot<ExternalAccountActions.Payload>()
        verify(timeout = 5_000) { externalActions.showExternalActions(capture(payload)) }
        verify { router.back() }
        assertEquals(RECIPIENT, payload.captured.value)
        assertTrue(payload.captured.explorers.isEmpty())
    }

    @Test fun `a flagged recipient opens the phishing alert that matches its type`() {
        val titles = mapOf(
            PhishingType.SCAM to R.string.scam_alert_title,
            PhishingType.EXCHANGE to R.string.exchange_alert_title,
            PhishingType.DONATION to R.string.donation_alert_title,
            PhishingType.SANCTIONS to R.string.sanction_alert_title,
            PhishingType.UNKNOWN to R.string.donation_alert_title
        )
        titles.forEach { (type, title) ->
            val vm = viewModel(extras = mapOf(ConfirmSendFragment.KEY_PHISHING_TYPE to type))
            await { vm.state.value.toInfoItem != null }
            val clickState = vm.state.value.toInfoItem?.clickState
            assertTrue(clickState is TitleValueViewState.ClickState.Value)
            assertEquals(ConfirmSendViewState.CODE_WARNING_CLICK, clickState?.identifier)

            vm.onItemClick(0) // not the warning: nothing opens
            vm.onItemClick(ConfirmSendViewState.CODE_WARNING_CLICK)

            val message = if (type == PhishingType.EXCHANGE) text(R.string.exchange_alert_message) else formatted(R.string.scam_alert_message_format)
            // DONATION and UNKNOWN share the donation title on this shared router mock.
            verify(exactly = if (type == PhishingType.UNKNOWN) 2 else 1, timeout = 5_000) {
                router.openAlert(AlertViewState(title = text(title), message = message, buttonText = text(R.string.top_up), iconRes = R.drawable.ic_alert_16))
            }
        }
    }

    @Test fun `a failed fee estimate shows a redacted error and keeps Confirm disabled`() {
        coEvery { interactor.observeTransferFee(any()) } returns flow<BigDecimal> { throw IllegalStateException("provider said: secret detail") }
        val vm = viewModel(waitForReady = false)

        await { vm.errorLiveData.value != null }

        assertEquals(text(R.string.fee_calculation_error), vm.errorLiveData.value?.peekContent())
        assertFalse(vm.state.value.buttonState.enabled)
        vm.onNextClick()
        assertEquals(0, validations.get())
    }

    @Test fun `a tip is listed, sent in planks, and the asset icon stands in for a missing network icon`() {
        val vm = viewModel(transferDraft = draftOf(tip = BigDecimal("0.5")))
        await { vm.state.value.tipInfoItem != null }

        assertEquals(text(R.string.choose_amount_tip), vm.state.value.tipInfoItem?.title)
        assertEquals(ICON_URL, vm.state.value.chainIconUrl)

        vm.onNextClick()
        await { performed.size == 1 }
        val params = performed.single().additionalParams as SubstrateTransferParams
        assertEquals(BigInteger("500000000000"), params.tipInPlanks)
        assertNull(params.appId)
    }

    @Test fun `a native review shows its amount, account rent and fee rate and sends the reviewed amount`() {
        val review = NativeSendReview(
            amount = BigDecimal("2"),
            networkFee = BigDecimal("0.25"),
            accountCreation = BigDecimal("0.5"),
            satPerVbyte = 4.0
        )
        val store = mockk<NativeSendQuoteStore> { every { reviews } returns MutableStateFlow(mapOf(QUOTE_ID to review)) }
        val vm = viewModel(nativeQuotes = store)
        await { vm.state.value.feeRateInfoItem != null }

        assertEquals(text(R.string.send_account_creation), vm.state.value.accountCreationInfoItem?.title)
        assertEquals(text(R.string.send_fee_rate), vm.state.value.feeRateInfoItem?.title)
        assertEquals(formatted(R.string.send_sat_per_vbyte), vm.state.value.feeRateInfoItem?.value)
        assertEquals(text(R.string.common_amount), vm.state.value.amountInfoItem?.title)

        vm.onNextClick()
        await { performed.size == 1 }
        assertEquals(BigDecimal("2"), performed.single().amount)
    }

    @Test fun `a blocking validation result is shown with its title and nothing is sent`() {
        validation = Result.success(TransferValidationResult.InsufficientBalance)
        val vm = viewModel()

        vm.onNextClick()
        await { vm.errorWithTitleLiveData.value != null }

        assertEquals(
            text(CommonR.string.common_not_enough_funds_title) to text(CommonR.string.choose_amount_error_too_big),
            vm.errorWithTitleLiveData.value?.peekContent()
        )
        assertTrue(performed.isEmpty())
    }

    @Test fun `an invalid transfer status shows the matching warning, then the matching error`() {
        submission = Result.failure(NotValidTransferStatus(TransferValidityLevel.Warning.Status.WillRemoveAccount))
        val vm = viewModel()

        vm.onNextClick()
        verify(timeout = 5_000) { validity.showTransferWarning(TransferValidityLevel.Warning.Status.WillRemoveAccount) }
        await { !vm.state.value.isLoading && vm.state.value.buttonState.enabled }

        submission = Result.failure(NotValidTransferStatus(TransferValidityLevel.Error.Status.NotEnoughFunds))
        vm.onNextClick()
        verify(timeout = 5_000) { validity.showTransferError(TransferValidityLevel.Error.Status.NotEnoughFunds) }
        verify(exactly = 0) { router.finishSendFlow() }
    }

    @Test fun `a payment that is still pending elsewhere is named as such`() {
        submission = Result.failure(NativeSendException(NativeSendException.Code.PAYMENT_PENDING))
        val vm = viewModel()

        vm.onNextClick()
        await { vm.errorLiveData.value != null }

        assertEquals(text(R.string.send_existing_pending), vm.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.finishSendFlow() }
    }

    @Test fun `a Bitcoin send skips the existential deposit check and announces the submission`() {
        every { chain.externalApi } returns Chain.ExternalApi(
            staking = null,
            history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.BITCOIN, "https://history.local"),
            crowdloans = null
        )
        val vm = viewModel()
        await { vm.state.value.nativeReview }

        vm.onNextClick()

        verify(timeout = 5_000) {
            router.openOperationSuccess(HASH, CHAIN, text(R.string.send_submitted_message), text(R.string.send_submitted_title))
        }
        verify { router.finishSendFlow() }
        assertEquals(0, validations.get())
        val transfer = performed.single()
        assertEquals(QUOTE_ID, transfer.quoteId)
        assertEquals(BigDecimal.ONE, transfer.estimateFee)
    }

    @Test fun `a TON transfer carries its memo`() {
        every { chain.ecosystem } returns Ecosystem.Ton
        val vm = viewModel(transferDraft = draftOf(message = "memo"))

        vm.onNextClick()
        await { performed.size == 1 }

        assertEquals("memo", (performed.single().additionalParams as TonTransferParams).comment)
        assertNull(performed.single().quoteId)
    }

    @Test fun `an Ethereum transfer carries no extra parameters`() {
        every { chain.ecosystem } returns Ecosystem.Ethereum
        val vm = viewModel()

        vm.onNextClick()
        await { performed.size == 1 }

        assertNull(performed.single().additionalParams)
        assertEquals(BigDecimal("1.01"), performed.single().estimateFee)
    }

    @Test fun `bokolo cash without enough fee asset reserves a quoted swap for the fee`() {
        every { chainAsset.currencyId } returns bokoloCashTokenId
        every { chainAsset.isUtility } returns false
        val utilityConfig = mockk<ChainAsset>(relaxed = true) {
            every { chainId } returns CHAIN
            every { id } returns "utility"
            every { symbol } returns "XOR"
            every { precision } returns 12
            every { isUtility } returns true
        }
        val utility = asset.copy(
            token = asset.token.copy(configuration = utilityConfig),
            freeInPlanksState = AssetAmountState.Known(BigInteger("500000000000"))
        )
        every { chain.assets } returns listOf(chainAsset, utilityConfig)
        every { interactor.assetFlow(CHAIN, "utility") } returns MutableStateFlow(utility)
        val quote = mockk<SwapDetails> { every { amount } returns BigDecimal("2") }
        coEvery { polkaswap.calcDetails(any(), any(), any(), any(), any(), any(), any()) } answers { Result.success(quote) }
        val vm = viewModel(extras = mapOf(ConfirmSendFragment.KEY_TRANSFER_COMMENT to "note"))

        vm.onNextClick()
        await { performed.size == 1 }

        val params = performed.single().additionalParams as CBDCTransferParams
        assertEquals("note", params.comment)
        assertEquals(BigInteger("3000000000000"), params.maxAmountInPlanks)
        coVerify { polkaswap.calcDetails(listOf(0), any(), any(), BigDecimal.ONE, WithDesired.OUTPUT, 1.5, Market.SMART) }
    }

    private fun draftOf(tip: BigDecimal? = null, message: String? = null) =
        TransferDraft(BigDecimal.TEN, BigDecimal.ONE, AssetPayload(CHAIN, "native"), RECIPIENT, tip, message)

    private fun viewModel(
        transferDraft: TransferDraft = draftOf(),
        extras: Map<String, Any?> = emptyMap(),
        nativeQuotes: NativeSendQuoteStore = NativeSendQuoteStore(),
        waitForReady: Boolean = true
    ): ConfirmSendViewModel {
        val arguments = mutableMapOf<String, Any?>(ConfirmSendFragment.KEY_DRAFT to transferDraft, QUOTE_KEY to QUOTE_ID)
        arguments.putAll(extras)
        return ConfirmSendViewModel(
            interactor = interactor,
            polkaswapInteractor = polkaswap,
            router = router,
            addressIconGenerator = mockk(relaxed = true),
            chainRegistry = chainRegistry,
            externalAccountActions = externalActions,
            transferValidityChecks = validity,
            savedStateHandle = SavedStateHandle(arguments),
            resourceManager = resources,
            currentAccountAddress = currentAddress,
            validateTransferUseCase = validator,
            nativeQuotes = nativeQuotes
        ).also { vm ->
            owned += vm
            if (waitForReady) await { vm.state.value.buttonState.enabled }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Expected local ViewModel state before timeout", condition())
    }

    private companion object {
        const val CHAIN = "local-confirmation-fixture"
        const val SENDER = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x2222222222222222222222222222222222222222"
        const val HASH = "local-fixture-hash"
        const val ICON_URL = "https://icons.local/native.svg"
        const val QUOTE_KEY = "native_send_quote_id"
        const val QUOTE_ID = "8d1e2f30-1111-4222-8333-944455556666"

        fun text(id: Int) = "text-$id"

        fun formatted(id: Int) = "fmt-$id"
    }
}
