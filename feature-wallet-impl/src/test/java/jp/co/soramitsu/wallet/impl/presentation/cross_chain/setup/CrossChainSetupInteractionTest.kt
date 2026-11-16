package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

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
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.ReviewedPolkaswapBridgeInteractor
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.PhishingModel
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainProviderContext
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrossChainSetupInteractionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
    private val config: jp.co.soramitsu.core.models.Asset = WalletTestModels.asset(chainId = "origin", priceId = "dot")
    private val asset: jp.co.soramitsu.wallet.impl.domain.model.Asset = WalletTestModels.fundedAsset(config)
    private val origin = WalletTestModels.chain(id = "origin", assets = listOf(config))
    private val destination = WalletTestModels.chain(id = "destination")
    private val meta = WalletTestModels.metaAccount()
    private var capability = CrossChainRouteCapability("reviewed-xcm", CrossChainProtocol.ReviewedXcm,
        CrossChainRouteAvailability.Available, true, true, true)
    private var validation: Result<TransferValidationResult> = Result.success(TransferValidationResult.Valid)
    private var phishing: PhishingModel? = null
    private val manager = mockk<ChainAssetsManager>(relaxed = true) {
        every { originChainIdFlow } returns MutableStateFlow("origin")
        every { destinationChainIdFlow } returns MutableStateFlow("destination")
        every { assetIdFlow } returns MutableStateFlow("dot")
        every { destinationChainId } returns "destination"
        every { assetSymbol } returns "DOT"
        every { assetFlow } returns flowOf(asset)
        every { originSelectedChain } returns flowOf(origin)
        every { destinationSelectedChainFlow } returns flowOf(destination)
        every { originChainSelectorStateFlow } returns flowOf(SelectorState.default)
        every { destinationChainSelectorStateFlow } returns flowOf(SelectorState.default)
        coEvery { routeCapability() } answers { capability }
        coEvery { routeInventory() } returns emptyList()
    }
    private val wallet = mockk<WalletInteractor>(relaxed = true) {
        coEvery { getChain("origin") } returns origin
        coEvery { getChain("destination") } returns destination
        every { assetFlow(any(), any()) } returns flowOf(asset)
        every { selectedAccountFlow(any()) } returns flowOf(WalletAccount(WalletTestModels.ALICE, "Main"))
        coEvery { validateSendAddress(any(), any()) } answers { secondArg<String>().isNotBlank() }
        coEvery { getPhishingInfo(any()) } answers { phishing }
        coEvery { tryReadAddressFromSoraFormat(any()) } returns null
    }
    private val accounts = mockk<AccountInteractor>(relaxed = true) {
        coEvery { selectedMetaAccount() } returns meta
        coEvery { getMetaAccount(any()) } returns meta
    }
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "res:" + firstArg<Int>() }
        every { getString(any(), *anyVararg()) } answers { "res:" + firstArg<Int>() }
    }
    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val xcm = mockk<XcmInteractor>(relaxed = true) {
        coEvery { getDestinationFee(any(), any(), any()) } returns BigDecimal("0.02")
        coEvery { getOriginFee(any(), any(), any(), any()) } returns BigDecimal("0.01")
        coEvery { getAmountMinLimit(any(), any(), any()) } returns BigInteger.ZERO
        coEvery { isDestinationFeeIncluded(any(), any(), any()) } returns false
    }
    private val validator = mockk<ValidateTransferUseCase> {
        coEvery { validateXcmTransfer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { validation }
    }
    private val bridge = mockk<ReviewedPolkaswapBridgeInteractor>()
    private val models = mutableListOf<CrossChainSetupViewModel>()

    @After fun dispose() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        models.forEach { it.viewModelScope.cancel() }
        await { jobs.all { it.isCompleted } }
    }

    @Test fun successfulTransferKeepsWalletNetworksFeesAndRecipient() {
        val draft = slot<CrossChainTransferDraft>()
        every { router.openCrossChainSendConfirm(capture(draft), any()) } returns Unit
        val vm = ready()
        vm.onNextClick()
        await { draft.isCaptured }
        assertEquals("origin", draft.captured.originChainId)
        assertEquals("destination", draft.captured.destinationChainId)
        assertEquals("dot", draft.captured.chainAssetId)
        assertEquals(BigDecimal("2"), draft.captured.amount)
        assertEquals(BigDecimal("0.01"), draft.captured.originFee)
        assertEquals(BigDecimal("0.02"), draft.captured.destinationFee)
        assertEquals(WalletTestModels.BOB, draft.captured.recipientAddress)
        assertEquals(1L, draft.captured.metaAccountId)
        assertFalse(draft.captured.isReviewedBridge)
        coVerify { validator.validateXcmTransfer(BigInteger("20000000000"), asset, "destination", WalletTestModels.BOB,
            WalletTestModels.ALICE, BigInteger("100000000"), any(), true, BigDecimal("0.02")) }
    }

    @Test fun validationFailureShowsTheErrorWithoutConfirmation() {
        validation = Result.failure(IllegalStateException("Recipient balance unavailable"))
        val vm = ready(); vm.onNextClick()
        await { vm.errorLiveData.value != null }
        assertEquals("Recipient balance unavailable", vm.errorLiveData.value!!.peekContent())
        verify(exactly = 0) { router.openCrossChainSendConfirm(any(), any()) }
    }

    @Test fun invalidRecipientShowsValidationExplanation() {
        validation = Result.success(TransferValidationResult.InvalidAddress)
        val vm = ready(); vm.onNextClick()
        await { vm.errorWithTitleLiveData.value != null }
        assertEquals("res:" + R.string.transfer_address_not_valid_error_message, vm.errorWithTitleLiveData.value!!.peekContent().second)
        verify(exactly = 0) { router.openCrossChainSendConfirm(any(), any()) }
    }

    @Test fun warningRequiresConfirmationAndThenRevalidates() {
        val warning = TransferValidationResult.ExistentialDepositUnknown("DOT")
        validation = Result.success(warning)
        val vm = ready(); vm.onNextClick()
        await { vm.openValidationWarningEvent.value != null }
        assertEquals(warning, vm.openValidationWarningEvent.value!!.peekContent().first)
        validation = Result.success(TransferValidationResult.Valid)
        vm.warningConfirmed(warning)
        await { runCatching { verify { router.openCrossChainSendConfirm(any(), any()) } }.isSuccess }
        coVerify(exactly = 2) { validator.validateXcmTransfer(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun belowMinimumExplainsTheRequiredAmount() {
        coEvery { xcm.getAmountMinLimit(any(), any(), any()) } returns BigInteger("30000000000")
        val vm = ready(); vm.onNextClick()
        await { vm.errorDialogStateLiveData.value != null }
        assertEquals("res:" + R.string.sora_bridge_low_amount_format_alert_2, vm.errorDialogStateLiveData.value!!.peekContent().message)
        coVerify(exactly = 0) { validator.validateXcmTransfer(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun routeDisabledAfterLoadingStopsContinue() {
        val vm = ready()
        capability = capability.copy(actionsEnabled = false, userFacingReason = "Route paused")
        vm.onNextClick()
        await { vm.state.value.routeCapabilityMessage == "Route paused" && !vm.state.value.buttonState.enabled }
        assertEquals("Route paused", vm.state.value.routeCapabilityMessage)
        assertFalse(vm.state.value.buttonState.enabled)
        verify(exactly = 0) { router.openCrossChainSendConfirm(any(), any()) }
    }

    @Test fun failedRouteDiscoveryDisablesActions() {
        coEvery { manager.routeCapability() } throws IllegalStateException("Discovery offline")
        val vm = model()
        await { vm.state.value.routeCapabilityMessage == "res:" + R.string.cross_chain_no_supported_route }
        assertFalse(vm.state.value.buttonState.enabled)
    }

    @Test fun inputAndNavigationCallbacksUseTheSelectedRoute() {
        val vm = ready()
        vm.onAmountFocusChanged(true); vm.setSoftKeyboardOpen(true)
        await { vm.state.value.amountInputState.isFocused && vm.state.value.isSoftKeyboardOpen }
        vm.onOriginChainClick(); vm.onDestinationChainClick(); vm.onAssetClick(); vm.onNavigationClick()
        verify { router.openSelectChainForXcm("origin", XcmChainType.Origin, null, null, null) }
        verify { router.openSelectChainForXcm("destination", XcmChainType.Destination, "origin", "dot", "DOT") }
        verify { router.openSelectAsset("origin", "dot", true); router.back() }
        vm.onAmountInput(null)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal.ZERO }
        assertFalse(vm.state.value.buttonState.enabled)
    }

    @Test fun clipboardHistoryQrAndClearReplaceTheRecipient() {
        val vm = ready()
        every { clipboard.getFromClipboard() } returns "clipboard-address"
        vm.onPasteClick()
        await { vm.state.value.addressInputState.input == "clipboard-address" }
        every { router.openAddressHistoryWithResult("origin") } returns flowOf("history-address")
        vm.onHistoryClick()
        await { vm.state.value.addressInputState.input == "history-address" }
        coEvery { wallet.tryReadAddressFromSoraFormat("sora-qr") } returns WalletTestModels.BOB
        vm.qrCodeScanned("sora-qr")
        await { vm.state.value.addressInputState.input == WalletTestModels.BOB }
        vm.onAddressInputClear()
        await { vm.state.value.addressInputState.input.isEmpty() }
        assertFalse(vm.state.value.buttonState.enabled)
    }

    @Test fun quickInputRoundsOnlyTheDisplayedAmount() {
        val vm = ready()
        vm.onQuickAmountInput(0.5)
        await { vm.state.value.amountInputState.tokenAmount == BigDecimal("40.12346") }
        assertEquals(BigDecimal("40.12346"), vm.state.value.amountInputState.tokenAmount)
        vm.onNextClick()
        await { runCatching { verify { router.openCrossChainSendConfirm(any(), any()) } }.isSuccess }
        coVerify { validator.validateXcmTransfer(BigInteger("401234567000"), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun walletSelectionUsesItsAddressOnTheDestinationNetwork() {
        val vm = ready()
        every { router.openWalletSelectorForResult(any(), any()) } returns flowOf(1L)
        vm.onMyWalletsClick()
        await { vm.state.value.addressInputState.input == meta.address(destination) }
        assertEquals(meta.address(destination), vm.state.value.addressInputState.input)
        coVerify { accounts.getMetaAccount(1L) }
    }

    @Test fun phishingCategoriesShowTheirDetailsAndExpansion() {
        val vm = ready()
        PhishingType.entries.forEach { type ->
            phishing = PhishingModel("address", "Reported account", type, "Category")
            vm.onAddressInput(WalletTestModels.BOB + type.name)
            await { vm.state.value.warningInfoState?.extras?.any { it.second == type.capitalizedName } == true }
            assertEquals(3, vm.state.value.warningInfoState!!.extras.size)
        }
        vm.onWarningInfoClick()
        await { vm.state.value.warningInfoState?.isExpanded == true }
        assertTrue(vm.state.value.warningInfoState!!.isExpanded)
    }

    @Test fun reviewedBridgeQuoteIsBoundToItsExecutionAndRuntime() {
        val quote = setupBridge()
        val draft = slot<CrossChainTransferDraft>()
        every { router.openCrossChainSendConfirm(capture(draft), any()) } returns Unit
        val vm = ready(); vm.onNextClick()
        await { draft.isCaptured }
        assertTrue(draft.captured.isReviewedBridge)
        assertEquals("bridge", draft.captured.providerId)
        assertEquals("route", draft.captured.routeId)
        assertEquals(quote.runtimeFingerprint, draft.captured.runtimeFingerprint)
        assertEquals(quote.executionFingerprint, draft.captured.executionFingerprint)
        assertEquals("100000000", draft.captured.originFeeInPlanks)
        assertEquals("200000000", draft.captured.destinationFeeInPlanks)
        assertEquals("1", draft.captured.effectiveMinimumInPlanks)
        coVerify(exactly = 0) { validator.validateXcmTransfer(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun changedBridgeRuntimeCannotOpenConfirmation() {
        val quote = setupBridge()
        val vm = ready()
        every { quote.runtimeFingerprint } returns "changed-runtime"
        vm.onNextClick()
        await { vm.errorLiveData.value != null }
        assertEquals("cross_chain_runtime_fingerprint_changed", vm.errorLiveData.value!!.peekContent())
        verify(exactly = 0) { router.openCrossChainSendConfirm(any(), any()) }
    }

    @Test fun bridgeQuoteFailureIsReportedAtConfirmation() {
        setupBridge()
        val vm = ready()
        coEvery { bridge.quote(any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("Quote expired")
        vm.onNextClick()
        await { vm.errorLiveData.value != null }
        assertEquals("Quote expired", vm.errorLiveData.value!!.peekContent())
        verify(exactly = 0) { router.openCrossChainSendConfirm(any(), any()) }
    }

    private fun setupBridge(): ReviewedBridgeQuote {
        capability = capability.copy(providerId = "bridge", providerContext = CrossChainProviderContext("bridge", "route", "runtime"))
        val quote = mockk<ReviewedBridgeQuote> {
            every { runtimeFingerprint } returns "runtime"
            every { executionFingerprint } returns "execution"
            every { originFeeInPlanks } returns BigInteger("100000000")
            every { destinationFeeInPlanks } returns BigInteger("200000000")
            every { effectiveMinimumInPlanks } returns BigInteger.ONE
        }
        coEvery { bridge.quote(any(), any(), any(), any(), any(), any(), any()) } returns quote
        return quote
    }

    private fun ready(): CrossChainSetupViewModel {
        val vm = model()
        await { vm.state.value.originFeeInfoState.feeAmount != null || capability.providerContext != null }
        main.dispatcher.scheduler.runCurrent()
        vm.onAddressInput(WalletTestModels.BOB); vm.onAmountInput(BigDecimal("2"))
        await { vm.state.value.buttonState.enabled }
        return vm
    }

    private fun model(): CrossChainSetupViewModel {
        val icons = mockk<AddressIconGenerator>(relaxed = true) {
            coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        }
        val address = mockk<CurrentAccountAddressUseCase>().also { coEvery { it.invoke(any()) } returns WalletTestModels.ALICE }
        val deposit = mockk<ExistentialDepositUseCase>().also { coEvery { it.invoke(any()) } returns Result.success(BigInteger.ZERO) }
        val quick = mockk<QuickInputsUseCase> {
            coEvery { calculateXcmTransfersQuickInputs(any(), any(), any()) } returns mapOf(0.5 to BigDecimal("40.1234567"))
        }
        val constants = mockk<WalletConstants> { coEvery { tip(any()) } returns BigInteger.ONE }
        return CrossChainSetupViewModel(SavedStateHandle(mapOf(CrossChainSetupFragment.KEY_PAYLOAD to AssetPayload("origin", "dot"))),
            accounts, resources, wallet, constants, router, clipboard, icons, address, validator, manager, xcm,
            bridge, deposit, quick).also { models += it }
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
