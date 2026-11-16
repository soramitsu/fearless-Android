package jp.co.soramitsu.polkamarkt.impl.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.polkamarkt.api.PolkamarktAccountCapability
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktHistoryPoint
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktPosition
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktSnapshot
import jp.co.soramitsu.polkamarkt.api.PolkamarktTrade
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
class PolkamarktScreenTest {
    @get:Rule val compose = createComposeRule()
    private val vm = mockk<PolkamarktViewModel>(relaxed = true)
    private val state = MutableStateFlow(PolkamarktUiState())
    private val market = PolkamarktMarket(
        id = "7", title = "Will it rain?", description = "Rain recorded at noon", category = "Weather",
        displayStatus = PolkamarktDisplayStatus.Open, probabilityPercent = "65", liquidityUsd = "500", runtimeOnly = true
    )
    private val snapshot = PolkamarktSnapshot(
        currentBlock = "100", markets = listOf(market), history = emptyList(), positions = emptyList(), trades = emptyList(),
        claimable = emptyList(), capabilities = PolkamarktRuntimeCapabilities(true, true, true, true, true, true, true, true),
        account = PolkamarktAccountCapability("alice", true, true, true), indexerStale = false, warnings = emptyList()
    )

    private fun render(initial: PolkamarktUiState) {
        state.value = initial
        every { vm.state } returns state
        val fragment = PolkamarktFragment()
        // Host the public Content entry point without attaching Hilt or the wallet graph.
        PolkamarktFragment::class.java.getDeclaredField("viewModel\$delegate").apply {
            isAccessible = true
            set(fragment, lazyOf(vm))
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        compose.setContent {
            FearlessAppTheme {
                fragment.Content(PaddingValues(), rememberScrollState(), rememberModalBottomSheetState(ModalBottomSheetValue.Hidden))
            }
        }
    }

    private fun click(text: String) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    @Test fun loadingAndFailureOfferBackAndRetry() {
        render(PolkamarktUiState())
        compose.onNodeWithText("Loading SORA markets…").assertIsDisplayed()
        compose.runOnIdle { state.value = PolkamarktUiState(loading = false, message = "Indexer offline") }
        compose.onNodeWithText("Indexer offline").assertIsDisplayed()
        click("Try again")
        click("Back")
        verify {
            vm.refresh()
            vm.back()
        }
    }

    @Test fun catalogShowsRuntimeMarketsAndRoutesSearchFiltersAndSelection() {
        render(PolkamarktUiState(loading = false, snapshot = snapshot, disclaimerAccepted = true, mutationsEnabled = true))
        compose.onNodeWithText("Detected directly on SORA runtime").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yes 65% · TVL 500 KUSD").assertIsDisplayed()
        click("Will it rain?")
        click("Finalized")
        click("All")
        compose.onNode(hasSetTextAction()).performTextReplacement("snow")
        verify {
            vm.selectMarket("7")
            vm.setFilter(MarketFilter.Finalized)
            vm.setFilter(MarketFilter.All)
            vm.setQuery("snow")
        }
    }

    @Test fun emptyCatalogAndUnavailableAccountExplainTheirStates() {
        val unavailable = snapshot.copy(markets = emptyList(), indexerStale = true, account = PolkamarktAccountCapability(reason = "Add SORA"))
        render(PolkamarktUiState(loading = false, snapshot = unavailable))
        compose.onNodeWithText("Indexer data may be stale; runtime status remains authoritative.").assertExists()
        compose.onNodeWithText("Add SORA").assertExists()
        compose.onNodeWithText("Trading and claims are temporarily disabled. Markets remain visible.").assertExists()
        compose.onNodeWithText("No markets are available yet.").performScrollTo().assertIsDisplayed()
        click("Review SORA risk disclaimer")
        verify { vm.openDisclaimer() }
    }

    @Test fun catalogFiltersClosedMarketsAndMatchesQueriesIgnoringCase() {
        val closed = market.copy(id = "8", title = "Snow tomorrow", displayStatus = PolkamarktDisplayStatus.Resolved)
        render(PolkamarktUiState(loading = false, snapshot = snapshot.copy(markets = listOf(market, closed)), disclaimerAccepted = true))
        compose.onNodeWithText("Snow tomorrow").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(filter = MarketFilter.Finalized, query = "SNOW") }
        compose.onNodeWithText("Snow tomorrow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Will it rain?").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(query = "missing") }
        compose.onNodeWithText("No active markets. Closed markets remain available under Finalized.").performScrollTo().assertIsDisplayed()
    }

    @Test fun marketDetailDisplaysQuoteAndForwardsTradingControls() {
        val quote = PolkamarktQuote("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "3", "0.1", "0.02", "5", "4.95")
        val detailed = market.copy(oracle = "oracle", resolutionSource = "station", closeBlock = "200")
        val history = listOf(PolkamarktHistoryPoint("a", "7", probabilityPercent = "25"), PolkamarktHistoryPoint("b", "7", probabilityPercent = "65"))
        val trade = PolkamarktTrade("t", "7", side = "buy", outcome = "Yes", collateral = "3", blockNumber = "90")
        render(
            PolkamarktUiState(
            loading = false, snapshot = snapshot.copy(markets = listOf(detailed), history = history, trades = listOf(trade)),
            selectedMarketId = "7", amount = "3", quote = quote, disclaimerAccepted = true, mutationsEnabled = true
        )
        )
        compose.onNodeWithText("Oracle: oracle").assertExists()
        compose.onNodeWithText("25% → 65%").performScrollTo().assertIsDisplayed()
        click("Sell")
        click("No")
        compose.onNode(hasSetTextAction()).performTextReplacement("4")
        click("Get runtime quote")
        compose.onNodeWithText("Receive 5 shares · minimum 4.95").performScrollTo().assertIsDisplayed()
        click("Confirm buy")
        compose.onNodeWithText("buy Yes · 3 · block 90").performScrollTo().assertIsDisplayed()
        click("Back")
        verify {
            vm.setTradeMode(PolkamarktTradeMode.Sell)
        vm.setOutcome(PolkamarktOutcome.No)
        vm.setAmount("4")
        }
        verify {
            vm.requestQuote()
        vm.confirmTrade()
        vm.selectMarket(null)
        }
    }

    @Test fun unsupportedQuoteAndWatchOnlyAccountDisableTradeActions() {
        val quote = PolkamarktQuote("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "3", "0", "0.02", "2", "1.98")
        val disabled = snapshot.copy(capabilities = snapshot.capabilities.copy(quoteSell = false), account = snapshot.account.copy(signable = false))
        render(
            PolkamarktUiState(
                loading = false, snapshot = disabled, selectedMarketId = "7", amount = "3",
            tradeMode = PolkamarktTradeMode.Sell, quote = quote, mutationsEnabled = true, disclaimerAccepted = true
            )
        )
        compose.onNodeWithText("Probability history is unavailable.").assertExists()
        compose.onNodeWithText("Shares amount").assertExists()
        compose.onNodeWithText("Get runtime quote").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Confirm sell").performScrollTo().assertIsNotEnabled()
        verify(exactly = 0) { vm.confirmTrade() }
    }

    @Test fun positionsExposeSeparateTraderAndCreatorClaims() {
        val position = PolkamarktPosition("p", "7", marketTitle = "Rain position", outcome = "Yes", shares = "10")
        val claim = PolkamarktClaimable("7", "alice", "Resolved", traderPayout = "20", creatorFees = "2", isCreator = true)
        render(
            PolkamarktUiState(
                loading = false, snapshot = snapshot.copy(positions = listOf(position), claimable = listOf(claim)),
            section = PolkamarktSection.Positions, disclaimerAccepted = true, mutationsEnabled = true
            )
        )
        click("Rain position")
        compose.onNodeWithText("Yes · 10 shares").assertExists()
        compose.onNodeWithText("Claim trader payout").performScrollTo().assertIsEnabled()
        click("Claim trader payout")
        click("Claim creator fees")
        verify {
            vm.selectMarket("7")
        vm.claimTrader("7")
        vm.claimCreator("7")
        }
    }

    @Test fun marketBundleOnlyIncludesProvidedId() {
        assertTrue(PolkamarktFragment.bundle(null).isEmpty)
        assertEquals("7", PolkamarktFragment.bundle("7").getString("marketId"))
    }
}
