package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SwapTokensViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val interactor = mockk<PolkaswapInteractor>(relaxed = true)
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val quickInputs = mockk<QuickInputsUseCase>()
    private val settings = MutableSharedFlow<TransactionSettingsModel>(extraBufferCapacity = 1)
    private val assetSelection = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val previewResult = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val accepted = MutableStateFlow(true)
    private val from = asset("xor", "XOR")
    private val to = asset("val", "VAL")
    private val models = mutableListOf<SwapTokensViewModel>()
    private lateinit var vm: SwapTokensViewModel

    private fun asset(id: String, symbol: String): Asset {
        val config = mockk<jp.co.soramitsu.core.models.Asset>(relaxed = true) {
            every { this@mockk.id } returns id
            every { currencyId } returns "currency-$id"
            every { this@mockk.symbol } returns symbol
            every { precision } returns 2
            every { iconUrl } returns ""
            every { color } returns "#000000"
        }
        val token = mockk<Token>(relaxed = true) {
            every { configuration } returns config
            every { fiatRate } returns null
            every { fiatSymbol } returns null
            every { fiatAmount(any()) } returns null
        }
        return mockk {
            every { this@mockk.token } returns token
            every { transferable } returns BigDecimal("100")
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "format:${firstArg<Int>()}" }
        every { interactor.polkaswapChainId } returns "sora"
        every { interactor.hasReadDisclaimer } returns true
        every { interactor.observeHasReadDisclaimer() } returns accepted
        every { interactor.assetFlow("xor") } returns flowOf(from)
        every { interactor.assetFlow("val") } returns flowOf(to)
        every { interactor.observePoolReserves(any(), any(), any()) } returns flowOf("reserves")
        every { interactor.bestDexIdFlow } returns MutableStateFlow(LoadingState.Loaded(0))
        every { interactor.availableMarkets } returns mutableMapOf(0 to Market.entries.toList())
        coEvery { interactor.getAvailableDexes() } returns listOf(BigInteger.ZERO)
        coEvery { interactor.getAvailableDexesForPair(any(), any(), any()) } returns listOf(0)
        coEvery { interactor.fetchAvailableSources(any(), any(), any()) } returns Market.entries.toSet()
        coEvery { interactor.calcFakeFee() } returns BigDecimal("0.01")
        coEvery { interactor.estimateSwapFee(any(), any(), any(), any(), any(), any()) } returns BigInteger.ONE
        coEvery { interactor.getFeeAsset() } returns from
        coEvery { interactor.getAsset("val") } returns to
        coEvery { interactor.calcDetails(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(
            SwapDetails(BigDecimal("7.5"), BigDecimal("7"), BigDecimal("3.75"), BigDecimal("0.26"), from, 0, "XOR → VAL")
        )
        coEvery { quickInputs.calculatePolkaswapQuickInputs(any(), any()) } returns mapOf(0.25 to BigDecimal("25.555555555"))
        every { router.observeResult<TransactionSettingsModel>(TransactionSettingsFragment.SETTINGS_MODEL_KEY) } returns settings
        every { router.observeResult<String>(WalletRouter.KEY_ASSET_ID) } returns assetSelection
        every { router.openSwapPreviewForResult(any(), any()) } returns previewResult
        vm = model(withAssets = true)
    }

    private fun model(withAssets: Boolean): SwapTokensViewModel {
        val handle = SavedStateHandle(mapOf(
            SwapTokensFragment.KEY_SELECTED_CHAIN_ID to "sora",
            SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID to "xor".takeIf { withAssets },
            SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID to "val".takeIf { withAssets }
        ))
        return SwapTokensViewModel(resources, interactor, router, quickInputs, handle).also { models += it }
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun enterFromAmount() {
        vm.onFromAmountFocusChange(true)
        dispatcher.scheduler.runCurrent()
        vm.onFromAmountChange(BigDecimal("2"))
        dispatcher.scheduler.runCurrent()
    }

    @Test fun selectedAssetsPopulateInputsAndCalculateQuoteAndFee() = runTest(dispatcher) {
        runCurrent()
        assertEquals("XOR", vm.state.value.fromAmountInputViewState.tokenName)
        assertEquals("VAL", vm.state.value.toAmountInputViewState.tokenName)
        enterFromAmount()
        assertEquals(0, BigDecimal("7.5").compareTo(vm.state.value.toAmountInputViewState.tokenAmount))
        assertEquals("XOR → VAL", vm.state.value.swapDetailsViewState?.route)
        assertNotNull((vm.state.value.networkFeeViewState as LoadingState.Loaded).data)
        coVerify { interactor.calcDetails(listOf(0), from, to, BigDecimal("2"), WithDesired.INPUT, 0.5, Market.SMART) }
    }

    @Test fun outputFocusCalculatesRequiredInputAndMaximumTooltip() = runTest(dispatcher) {
        runCurrent()
        vm.onToAmountFocusChange(true); runCurrent()
        vm.onToAmountChange(BigDecimal("3")); runCurrent()
        coVerify { interactor.calcDetails(listOf(0), from, to, BigDecimal("3"), WithDesired.OUTPUT, 0.5, Market.SMART) }
        assertEquals(0, BigDecimal("7.5").compareTo(vm.state.value.fromAmountInputViewState.tokenAmount))
        vm.minMaxToolTopClick()
        assertNotNull(vm.showTooltipEvent.value)
    }

    @Test fun previewCarriesExactAmountsAndSuccessfulResultClearsInputs() = runTest(dispatcher) {
        runCurrent(); enterFromAmount()
        val parcel = slot<SwapDetailsParcelModel>()
        every { router.openSwapPreviewForResult(any(), capture(parcel)) } returns previewResult
        vm.onPreviewClick(); runCurrent()
        assertEquals(BigInteger("200"), parcel.captured.amount)
        assertEquals(WithDesired.INPUT, parcel.captured.desired)
        assertFalse(vm.state.value.isLoading)
        previewResult.emit(Activity.RESULT_OK); runCurrent()
        assertEquals(0, vm.state.value.fromAmountInputViewState.tokenAmount.signum())
        assertEquals(0, vm.state.value.toAmountInputViewState.tokenAmount.signum())
    }

    @Test fun missingFeeOrUnavailableBalancePreventsPreview() = runTest(dispatcher) {
        runCurrent()
        vm.onPreviewClick(); runCurrent()
        verify(exactly = 0) { router.openSwapPreviewForResult(any(), any()) }
        assertNotNull(vm.errorWithTitleLiveData.value)
        enterFromAmount()
        every { from.transferable } returns null
        vm.onPreviewClick(); runCurrent()
        verify(exactly = 0) { router.openSwapPreviewForResult(any(), any()) }
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun settingsResultChangesMarketAndSlippageUsedForSubsequentQuotes() = runTest(dispatcher) {
        runCurrent(); enterFromAmount()
        settings.emit(TransactionSettingsModel(Market.XYK, 1.0)); runCurrent()
        assertEquals(Market.XYK, vm.state.value.selectedMarket)
        vm.onMarketSettingsClick()
        verify { router.openTransactionSettingsDialog(TransactionSettingsModel(Market.XYK, 1.0)) }
        coVerify { interactor.calcDetails(listOf(0), from, to, BigDecimal("2"), WithDesired.INPUT, 1.0, Market.XYK) }
    }

    @Test fun swappingAssetsExchangesInputValuesAndTokenIdentities() = runTest(dispatcher) {
        runCurrent()
        vm.onFromAmountChange(BigDecimal("2"))
        vm.onToAmountChange(BigDecimal("3")); runCurrent()
        vm.onChangeTokensClick(); runCurrent()
        assertEquals("VAL", vm.state.value.fromAmountInputViewState.tokenName)
        assertEquals("XOR", vm.state.value.toAmountInputViewState.tokenName)
    }

    @Test fun assetPickerExcludesOtherTokenAndUsesSelectedResult() = runTest(dispatcher) {
        runCurrent()
        vm.onFromTokenSelect(); runCurrent()
        verify { router.openSelectAsset("sora", "xor", "val", AssetSelectPurpose.SWAP_FROM) }
        assetSelection.emit("val"); runCurrent()
        assertEquals("VAL", vm.state.value.fromAmountInputViewState.tokenName)
        vm.onToTokenSelect()
        verify { router.openSelectAsset("sora", "val", "val", AssetSelectPurpose.SWAP_TO) }
    }

    @Test fun missingAssetsShowWarningAndOpenFromAssetPicker() = runTest(dispatcher) {
        val empty = model(withAssets = false); runCurrent()
        empty.onMarketSettingsClick()
        assertNotNull(empty.showMarketsWarningEvent.value)
        assertNull(empty.state.value.fromAmountInputViewState.tokenName)
        empty.marketAlertConfirmed()
        verify { router.openSelectAsset("sora", null, null, AssetSelectPurpose.SWAP_FROM) }
    }

    @Test fun quickInputUsesComputedAmountAndAuxiliaryActionsReachRouter() = runTest(dispatcher) {
        runCurrent(); enterFromAmount()
        vm.onQuickAmountInput(0.25); runCurrent()
        assertEquals(BigDecimal("25.55555555"), vm.state.value.fromAmountInputViewState.tokenAmount)
        vm.networkFeeTooltipClick()
        assertNotNull(vm.showTooltipEvent.value)
        vm.onLiquidityBannerClose()
        vm.setSoftKeyboardOpen(true)
        accepted.value = false
        runCurrent()
        assertFalse(vm.state.value.showLiquidityBanner)
        assertTrue(vm.state.value.isSoftKeyboardOpen)
        assertFalse(vm.state.value.hasReadDisclaimer)
        vm.onBackClick(); vm.onPoolsClick(); vm.onDisclaimerClick()
        verify { router.back(); router.openPools(); router.openPolkaswapDisclaimerFromSwapTokensFragment() }
    }
}
