package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Bug 35: a token pair with no route raised an error without a message and of a type the screen does not
 * explain, so the user saw nothing; and an exception thrown while quoting (or estimating the fee) ended the
 * pipeline, so later amounts got no quote at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapQuotePipelineTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val interactor = mockk<PolkaswapInteractor>(relaxed = true)
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val quickInputs = mockk<QuickInputsUseCase>()
    private val from = asset("xor", "XOR")
    private val to = asset("val", "VAL")
    private lateinit var viewModel: SwapTokensViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "format:${firstArg<Int>()}" }
        every { interactor.polkaswapChainId } returns "sora"
        every { interactor.hasReadDisclaimer } returns true
        every { interactor.observeHasReadDisclaimer() } returns MutableStateFlow(true)
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
        coEvery { interactor.calcDetails(any(), any(), any(), any(), any(), any(), any()) } answers {
            Result.success(quote(arg(3)))
        }
        coEvery { quickInputs.calculatePolkaswapQuickInputs(any(), any()) } returns emptyMap()
        every {
            router.observeResult<TransactionSettingsModel>(TransactionSettingsFragment.SETTINGS_MODEL_KEY)
        } returns MutableSharedFlow()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun shouldExplainTheMissingRoute_whenThePairHasNoDexPath() = runTest(dispatcher) {
        coEvery { interactor.getAvailableDexesForPair(any(), any(), any()) } returns emptyList()

        viewModel = swapViewModel()
        runCurrent()

        assertEquals(
            "string:${R.string.common_error_general_title}" to "string:${R.string.polkaswap_path_unavailable_message}",
            viewModel.errorWithTitleLiveData.value?.peekContent()
        )
    }

    @Test
    fun shouldQuoteLaterAmounts_whenAQuoteThrew() = runTest(dispatcher) {
        var quoteFails = true
        coEvery { interactor.calcDetails(any(), any(), any(), any(), any(), any(), any()) } answers {
            if (quoteFails) error("quote node is down") else Result.success(quote(arg(3)))
        }
        viewModel = swapViewModel()
        runCurrent()

        enterFromAmount("2")
        assertEquals("quote node is down", viewModel.errorLiveData.value?.peekContent())
        assertNull(viewModel.state.value.swapDetailsViewState)

        quoteFails = false
        enterFromAmount("3")

        coVerify { interactor.calcDetails(listOf(0), from, to, BigDecimal("3"), WithDesired.INPUT, 0.5, Market.SMART) }
        assertNotNull(viewModel.state.value.swapDetailsViewState)
        assertEquals(0, BigDecimal("7.5").compareTo(viewModel.state.value.toAmountInputViewState.tokenAmount))
    }

    @Test
    fun shouldEstimateTheFeeOfLaterQuotes_whenAFeeEstimateThrew() = runTest(dispatcher) {
        var feeFails = true
        coEvery { interactor.estimateSwapFee(any(), any(), any(), any(), any(), any()) } answers {
            if (feeFails) error("fee node is down") else BigInteger.ONE
        }
        viewModel = swapViewModel()
        runCurrent()

        enterFromAmount("2")
        assertNull(viewModel.state.value.networkFeeViewState.dataOrNull())

        feeFails = false
        enterFromAmount("3")

        assertNotNull(viewModel.state.value.networkFeeViewState.dataOrNull())
    }

    private fun kotlinx.coroutines.test.TestScope.enterFromAmount(amount: String) {
        viewModel.onFromAmountFocusChange(true)
        runCurrent()
        viewModel.onFromAmountChange(BigDecimal(amount))
        runCurrent()
    }

    // A quote of 2.5 VAL per XOR.
    private fun quote(amount: BigDecimal) = SwapDetails(
        amount = amount.multiply(BigDecimal("2.5")),
        minMax = amount.multiply(BigDecimal("2.4")),
        fromTokenOnToToken = BigDecimal("2.5"),
        toTokenOnFromToken = BigDecimal("0.4"),
        feeAsset = from,
        bestDexId = 0,
        route = "XOR -> VAL"
    )

    private fun swapViewModel() = SwapTokensViewModel(
        resources,
        interactor,
        router,
        quickInputs,
        SavedStateHandle(
            mapOf(
                SwapTokensFragment.KEY_SELECTED_CHAIN_ID to "sora",
                SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID to "xor",
                SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID to "val"
            )
        )
    )

    private fun asset(id: String, symbol: String): Asset {
        val configuration = mockk<jp.co.soramitsu.core.models.Asset>(relaxed = true) {
            every { this@mockk.id } returns id
            every { currencyId } returns "currency-$id"
            every { this@mockk.symbol } returns symbol
            every { precision } returns 18
            every { iconUrl } returns ""
            every { color } returns "#000000"
        }
        val token = mockk<Token>(relaxed = true) {
            every { this@mockk.configuration } returns configuration
            every { fiatRate } returns null
            every { fiatSymbol } returns null
            every { fiatAmount(any()) } returns null
        }
        return mockk {
            every { this@mockk.token } returns token
            every { transferable } returns BigDecimal("100")
        }
    }
}
