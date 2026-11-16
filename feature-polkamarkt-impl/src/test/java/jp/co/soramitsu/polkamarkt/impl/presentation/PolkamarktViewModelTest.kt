package jp.co.soramitsu.polkamarkt.impl.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuoteRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktRouter
import jp.co.soramitsu.polkamarkt.api.PolkamarktSnapshot
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolkamarktViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<PolkamarktInteractor>()
    private val router = mockk<PolkamarktRouter>(relaxed = true)
    private val swaps = mockk<PolkaswapInteractor>()
    private val toggles = mockk<ProductFeatureToggleStore>()
    private val accepted = MutableStateFlow(false)
    private val snapshot = mockk<PolkamarktSnapshot>()
    private val quote = PolkamarktQuote("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "3", "0.1", "0.02", "5", "4.95")
    private lateinit var vm: PolkamarktViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { toggles.polkamarktMutationsEnabled } returns true
        every { swaps.observeHasReadDisclaimer() } returns accepted
        coEvery { interactor.snapshot(any()) } returns snapshot
        coEvery { interactor.quote(any()) } returns quote
        coEvery { interactor.mutate(any()) } returns Result.success("0xaccepted")
        vm = PolkamarktViewModel(SavedStateHandle(mapOf("marketId" to "7")), interactor, router, swaps, toggles)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun initialLoadKeepsRequestedMarketAndObservesDisclaimer() {
        assertTrue(vm.state.value.loading)
        runTest(dispatcher) {
            runCurrent()
            assertFalse(vm.state.value.loading)
            assertSame(snapshot, vm.state.value.snapshot)
            assertEquals("7", vm.state.value.selectedMarketId)
            assertTrue(vm.state.value.mutationsEnabled)
            accepted.value = true
            runCurrent()
            assertTrue(vm.state.value.disclaimerAccepted)
            coVerify { interactor.snapshot("7") }
        }
    }

    @Test fun refreshFailureKeepsLastSnapshotAndRetryClearsError() = runTest(dispatcher) {
        runCurrent()
        coEvery { interactor.snapshot(any()) } throws IllegalStateException("Node offline")
        vm.refresh()
        runCurrent()
        assertEquals("Node offline", vm.state.value.message)
        assertSame(snapshot, vm.state.value.snapshot)
        assertFalse(vm.state.value.loading)
        coEvery { interactor.snapshot(any()) } returns snapshot
        vm.refresh()
        runCurrent()
        assertNull(vm.state.value.message)
    }

    @Test fun catalogControlsRetainFilterAndQueryWhileSectionClearsSelection() = runTest(dispatcher) {
        runCurrent()
        vm.setFilter(MarketFilter.Finalized)
        vm.setQuery("weather")
        vm.setSection(PolkamarktSection.Positions)
        assertEquals(MarketFilter.Finalized, vm.state.value.filter)
        assertEquals("weather", vm.state.value.query)
        assertEquals(PolkamarktSection.Positions, vm.state.value.section)
        assertNull(vm.state.value.selectedMarketId)
        vm.selectMarket("8")
        runCurrent()
        coVerify { interactor.snapshot("8") }
    }

    @Test fun quoteBindsSelectedModeOutcomeAndAmountAndTracksPendingState() = runTest(dispatcher) {
        runCurrent()
        val pending = CompletableDeferred<PolkamarktQuote>()
        coEvery { interactor.quote(any()) } coAnswers { pending.await() }
        vm.setTradeMode(PolkamarktTradeMode.Sell)
        vm.setOutcome(PolkamarktOutcome.No)
        vm.setAmount("12.5")
        vm.requestQuote()
        runCurrent()
        assertTrue(vm.state.value.mutating)
        coVerify { interactor.quote(PolkamarktQuoteRequest("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "12.5")) }
        pending.complete(quote)
        runCurrent()
        assertSame(quote, vm.state.value.quote)
        assertFalse(vm.state.value.mutating)
    }

    @Test fun editingTradeInputsInvalidatesAnExistingQuote() = runTest(dispatcher) {
        runCurrent()
        vm.requestQuote(); runCurrent()
        vm.setAmount("4")
        assertNull(vm.state.value.quote)
        vm.requestQuote(); runCurrent()
        vm.setOutcome(PolkamarktOutcome.No)
        assertNull(vm.state.value.quote)
        vm.requestQuote(); runCurrent()
        vm.setTradeMode(PolkamarktTradeMode.Sell)
        assertNull(vm.state.value.quote)
        vm.requestQuote(); runCurrent()
        vm.selectMarket(null); runCurrent()
        assertNull(vm.state.value.quote)
    }

    @Test fun missingMarketOrQuoteDoesNotSubmitWork() = runTest(dispatcher) {
        runCurrent()
        vm.selectMarket(null)
        vm.requestQuote()
        vm.confirmTrade()
        runCurrent()
        coVerify(exactly = 0) { interactor.quote(any()) }
        coVerify(exactly = 0) { interactor.mutate(any()) }
        assertFalse(vm.state.value.mutating)
    }

    @Test fun quoteAndMutationFailuresExposeFallbackMessages() = runTest(dispatcher) {
        runCurrent()
        coEvery { interactor.quote(any()) } throws IllegalStateException()
        vm.requestQuote(); runCurrent()
        assertEquals("Quote unavailable.", vm.state.value.message)
        coEvery { interactor.mutate(any()) } returns Result.failure(IllegalStateException())
        vm.claimTrader("7"); runCurrent()
        assertEquals("Transaction failed.", vm.state.value.message)
        assertFalse(vm.state.value.mutating)
    }

    @Test fun confirmationUsesQuoteValuesAndRefreshesAfterSubmission() = runTest(dispatcher) {
        runCurrent()
        vm.requestQuote(); runCurrent()
        vm.confirmTrade(); runCurrent()
        coVerify { interactor.mutate(PolkamarktMutation.Trade("7", quote.mode, quote.outcome, "3", "4.95")) }
        coVerify(exactly = 2) { interactor.snapshot("7") }
        assertNull(vm.state.value.quote)
        assertFalse(vm.state.value.mutating)
    }

    @Test fun claimsUseDistinctRequestsAndNavigationDelegates() = runTest(dispatcher) {
        runCurrent()
        vm.claimTrader("9"); runCurrent()
        vm.claimCreator("10"); runCurrent()
        coVerify { interactor.mutate(PolkamarktMutation.ClaimMarket("9")) }
        coVerify { interactor.mutate(PolkamarktMutation.ClaimCreatorFees("10")) }
        vm.openDisclaimer()
        vm.back()
        verify { router.openRiskDisclaimer(); router.back() }
        assertFalse(vm.state.value.mutating)
    }
}
