package jp.co.soramitsu.polkamarkt.impl.presentation

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktHistoryPoint
import jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuoteRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktRouter
import jp.co.soramitsu.polkamarkt.api.PolkamarktSnapshot
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

private const val MARKET_ID_ARGUMENT = "marketId"

enum class PolkamarktSection { Markets, Positions }
enum class MarketFilter { Active, Finalized, All }

@Immutable
data class PolkamarktUiState(
    val loading: Boolean = true,
    val snapshot: PolkamarktSnapshot? = null,
    val selectedMarketId: String? = null,
    val section: PolkamarktSection = PolkamarktSection.Markets,
    val filter: MarketFilter = MarketFilter.Active,
    val query: String = "",
    val tradeMode: PolkamarktTradeMode = PolkamarktTradeMode.Buy,
    val outcome: PolkamarktOutcome = PolkamarktOutcome.Yes,
    val amount: String = "",
    val quote: PolkamarktQuote? = null,
    val disclaimerAccepted: Boolean = false,
    val mutationsEnabled: Boolean = false,
    val mutating: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PolkamarktViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val interactor: PolkamarktInteractor,
    private val router: PolkamarktRouter,
    private val polkaswapInteractor: PolkaswapInteractor,
    toggles: ProductFeatureToggleStore
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(
        PolkamarktUiState(
            selectedMarketId = savedStateHandle[MARKET_ID_ARGUMENT],
            mutationsEnabled = toggles.polkamarktMutationsEnabled
        )
    )
    val state: StateFlow<PolkamarktUiState> = mutableState

    init {
        refresh()
        polkaswapInteractor.observeHasReadDisclaimer().onEach { accepted ->
            mutableState.update { it.copy(disclaimerAccepted = accepted) }
        }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, message = null) }
            runCatching { interactor.snapshot(mutableState.value.selectedMarketId) }
                .onSuccess { snapshot -> mutableState.update { it.copy(loading = false, snapshot = snapshot) } }
                .onFailure { error -> mutableState.update { it.copy(loading = false, message = error.message ?: "Polkamarkt is unavailable.") } }
        }
    }

    fun selectMarket(id: String?) {
        mutableState.update { it.copy(selectedMarketId = id, quote = null, message = null) }
        refresh()
    }

    fun setSection(section: PolkamarktSection) = mutableState.update { it.copy(section = section, selectedMarketId = null) }
    fun setFilter(filter: MarketFilter) = mutableState.update { it.copy(filter = filter) }
    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }
    fun setTradeMode(mode: PolkamarktTradeMode) = mutableState.update { it.copy(tradeMode = mode, quote = null) }
    fun setOutcome(outcome: PolkamarktOutcome) = mutableState.update { it.copy(outcome = outcome, quote = null) }
    fun setAmount(amount: String) = mutableState.update { it.copy(amount = amount, quote = null) }
    fun openDisclaimer() = router.openRiskDisclaimer()
    fun back() = router.back()

    fun requestQuote() {
        val current = mutableState.value
        val marketId = current.selectedMarketId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(mutating = true, message = null) }
            runCatching {
                interactor.quote(
                    PolkamarktQuoteRequest(marketId, current.tradeMode, current.outcome, current.amount)
                )
            }.onSuccess { quote -> mutableState.update { it.copy(mutating = false, quote = quote) } }
                .onFailure { error -> mutableState.update { it.copy(mutating = false, message = error.message ?: "Quote unavailable.") } }
        }
    }

    fun confirmTrade() {
        val quote = mutableState.value.quote ?: return
        mutate(
            PolkamarktMutation.Trade(
                quote.marketId,
                quote.mode,
                quote.outcome,
                quote.amount,
                quote.minimumResult
            )
        )
    }

    fun claimTrader(marketId: String) = mutate(PolkamarktMutation.ClaimMarket(marketId))
    fun claimCreator(marketId: String) = mutate(PolkamarktMutation.ClaimCreatorFees(marketId))

    private fun mutate(request: PolkamarktMutation) {
        viewModelScope.launch {
            mutableState.update { it.copy(mutating = true, message = null) }
            interactor.mutate(request).onSuccess { hash ->
                mutableState.update { it.copy(mutating = false, quote = null, message = "Submitted: $hash") }
                refresh()
            }.onFailure { error ->
                mutableState.update { it.copy(mutating = false, message = error.message ?: "Transaction failed.") }
            }
        }
    }
}

@AndroidEntryPoint
class PolkamarktFragment : BaseComposeFragment<PolkamarktViewModel>() {
    override val viewModel: PolkamarktViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        PolkamarktScreen(
            state = state,
            scrollState = scrollState,
            onBack = {
                if (state.selectedMarketId != null) viewModel.selectMarket(null) else viewModel.back()
            },
            onRefresh = viewModel::refresh,
            onSection = viewModel::setSection,
            onFilter = viewModel::setFilter,
            onQuery = viewModel::setQuery,
            onSelectMarket = viewModel::selectMarket,
            onMode = viewModel::setTradeMode,
            onOutcome = viewModel::setOutcome,
            onAmount = viewModel::setAmount,
            onQuote = viewModel::requestQuote,
            onTrade = viewModel::confirmTrade,
            onDisclaimer = viewModel::openDisclaimer,
            onClaimTrader = viewModel::claimTrader,
            onClaimCreator = viewModel::claimCreator,
            onShare = ::shareMarket
        )
    }

    private fun shareMarket(marketId: String) {
        val link = "fearless://defi/polkamarkt/$marketId"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                },
                "Share Polkamarkt market"
            )
        )
    }

    companion object {
        fun bundle(marketId: String?): Bundle = Bundle().apply { marketId?.let { putString(MARKET_ID_ARGUMENT, it) } }
    }
}

@Composable
private fun PolkamarktScreen(
    state: PolkamarktUiState,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSection: (PolkamarktSection) -> Unit,
    onFilter: (MarketFilter) -> Unit,
    onQuery: (String) -> Unit,
    onSelectMarket: (String?) -> Unit,
    onMode: (PolkamarktTradeMode) -> Unit,
    onOutcome: (PolkamarktOutcome) -> Unit,
    onAmount: (String) -> Unit,
    onQuote: () -> Unit,
    onTrade: () -> Unit,
    onDisclaimer: () -> Unit,
    onClaimTrader: (String) -> Unit,
    onClaimCreator: (String) -> Unit,
    onShare: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GrayButton(text = "Back", onClick = onBack)
        H1(text = "Polkamarkt")
        B0(text = "SORA prediction markets · KUSD collateral · XOR network fees", color = white50)
        state.message?.let { B0(text = it, color = colorAccentDark) }
        if (state.loading && state.snapshot == null) {
            B0(text = "Loading SORA markets…", color = white50)
            return@Column
        }
        val snapshot = state.snapshot
        if (snapshot == null) {
            GrayButton(text = "Try again", onClick = onRefresh)
            return@Column
        }
        if (snapshot.indexerStale) B0(text = "Indexer data may be stale; runtime status remains authoritative.", color = white50)
        snapshot.account.reason?.let { B0(text = it, color = white50) }
        if (!state.mutationsEnabled) B0(text = "Trading and claims are temporarily disabled. Markets remain visible.", color = white50)
        if (!state.disclaimerAccepted) GrayButton(text = "Review SORA risk disclaimer", onClick = onDisclaimer)

        val selected = snapshot.markets.firstOrNull { it.id == state.selectedMarketId }
        if (selected != null) {
            MarketDetail(
                state,
                snapshot,
                selected,
                onMode,
                onOutcome,
                onAmount,
                onQuote,
                onTrade,
                onShare
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrayButton(modifier = Modifier.weight(1f), text = "Markets", onClick = { onSection(PolkamarktSection.Markets) })
            GrayButton(modifier = Modifier.weight(1f), text = "Your positions", onClick = { onSection(PolkamarktSection.Positions) })
        }
        if (state.section == PolkamarktSection.Positions) {
            Positions(snapshot, state, onSelectMarket, onClaimTrader, onClaimCreator)
        } else {
            MarketCatalog(state, snapshot, onFilter, onQuery, onSelectMarket)
        }
    }
}

@Composable
private fun MarketCatalog(
    state: PolkamarktUiState,
    snapshot: PolkamarktSnapshot,
    onFilter: (MarketFilter) -> Unit,
    onQuery: (String) -> Unit,
    onSelectMarket: (String?) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.query,
        onValueChange = onQuery,
        label = { Text("Search markets") },
        singleLine = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MarketFilter.entries.forEach { filter ->
            GrayButton(modifier = Modifier.weight(1f), text = filter.name, onClick = { onFilter(filter) })
        }
    }
    val markets = snapshot.markets.filter { market ->
        val statusMatches = when (state.filter) {
            MarketFilter.Active -> market.displayStatus in setOf(PolkamarktDisplayStatus.Open, PolkamarktDisplayStatus.Locked)
            MarketFilter.Finalized -> market.displayStatus !in setOf(PolkamarktDisplayStatus.Open, PolkamarktDisplayStatus.Locked)
            MarketFilter.All -> true
        }
        val queryMatches = state.query.isBlank() || listOf(market.title, market.category, market.id)
            .any { it.contains(state.query, ignoreCase = true) }
        statusMatches && queryMatches
    }
    if (markets.isEmpty()) {
        val closedExist = snapshot.markets.any { it.displayStatus != PolkamarktDisplayStatus.Open }
        B0(
            text = if (snapshot.markets.isEmpty()) "No markets are available yet." else if (closedExist) "No active markets. Closed markets remain available under Finalized." else "No markets match this filter.",
            color = white50
        )
    }
    markets.forEach { market -> MarketCard(market) { onSelectMarket(market.id) } }
}

@Composable
private fun MarketCard(market: PolkamarktMarket, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(white08, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            B0(text = "${market.category} · #${market.id}", color = white50)
            B0(text = market.displayStatus.name, color = white50)
        }
        H3(text = market.title)
        B0(text = "Yes ${market.probabilityPercent ?: "—"}% · TVL ${market.liquidityUsd} KUSD", color = white50)
        if (market.runtimeOnly) B0(text = "Detected directly on SORA runtime", color = white50)
    }
}

@Composable
private fun MarketDetail(
    state: PolkamarktUiState,
    snapshot: PolkamarktSnapshot,
    market: PolkamarktMarket,
    onMode: (PolkamarktTradeMode) -> Unit,
    onOutcome: (PolkamarktOutcome) -> Unit,
    onAmount: (String) -> Unit,
    onQuote: () -> Unit,
    onTrade: () -> Unit,
    onShare: (String) -> Unit
) {
    B0(text = "${market.category} · Market #${market.id} · ${market.displayStatus.name}", color = white50)
    H3(text = market.title)
    B0(text = market.description, color = white50)
    market.oracle?.let { B0(text = "Oracle: $it", color = white50) }
    market.resolutionSource?.let { B0(text = "Resolution source: $it", color = white50) }
    market.closeBlock?.let { B0(text = "Closes at SORA block $it · current ${snapshot.currentBlock}", color = white50) }
    B0(text = "Probability ${market.probabilityPercent ?: "unavailable"}% · TVL ${market.liquidityUsd} KUSD · Volume ${market.volumeUsd} KUSD")
    GrayButton(text = "Share market", onClick = { onShare(market.id) })

    H3(text = "Probability history")
    ProbabilityChart(snapshot.history.filter { it.marketId == market.id })

    if (market.displayStatus == PolkamarktDisplayStatus.Open) {
        H3(text = "Trade with KUSD")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrayButton(modifier = Modifier.weight(1f), text = "Buy", onClick = { onMode(PolkamarktTradeMode.Buy) })
            GrayButton(modifier = Modifier.weight(1f), text = "Sell", onClick = { onMode(PolkamarktTradeMode.Sell) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrayButton(modifier = Modifier.weight(1f), text = "Yes", onClick = { onOutcome(PolkamarktOutcome.Yes) })
            GrayButton(modifier = Modifier.weight(1f), text = "No", onClick = { onOutcome(PolkamarktOutcome.No) })
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.amount,
            onValueChange = onAmount,
            label = { Text(if (state.tradeMode == PolkamarktTradeMode.Buy) "KUSD amount" else "Shares amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        val quoteSupported = snapshot.capabilities.marketState &&
            if (state.tradeMode == PolkamarktTradeMode.Buy) snapshot.capabilities.quoteBuy else snapshot.capabilities.quoteSell
        GrayButton(
            text = "Get runtime quote",
            enabled = quoteSupported && state.amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true && !state.mutating,
            onClick = onQuote
        )
        state.quote?.let { quote ->
            B0(text = "Receive ${quote.resultAmount} shares · minimum ${quote.minimumResult}")
            B0(text = "Market fee ${quote.feeAmount} KUSD · network fee ${quote.networkFee} XOR", color = white50)
            val callAvailable = if (quote.mode == PolkamarktTradeMode.Buy) snapshot.capabilities.buy else snapshot.capabilities.sell
            AccentButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Confirm ${quote.mode.name.lowercase()}",
                enabled = callAvailable && state.disclaimerAccepted && state.mutationsEnabled && snapshot.account.signable &&
                    snapshot.account.hasXorForFees && (quote.mode != PolkamarktTradeMode.Buy || snapshot.account.hasKusd) && !state.mutating,
                onClick = onTrade
            )
        }
    }
    val trades = snapshot.trades.filter { it.marketId == market.id }
    if (trades.isNotEmpty()) {
        H3(text = "Recent activity")
        trades.forEach { trade -> B0(text = "${trade.side ?: "Trade"} ${trade.outcome.orEmpty()} · ${trade.collateral ?: trade.sharesIn.orEmpty()} · block ${trade.blockNumber ?: "—"}", color = white50) }
    }
}

@Composable
private fun ProbabilityChart(points: List<PolkamarktHistoryPoint>) {
    if (points.isEmpty()) {
        B0(text = "Probability history is unavailable.", color = white50)
        return
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).background(black50, RoundedCornerShape(10.dp))) {
        val values = points.mapNotNull { it.probabilityPercent.toFloatOrNull()?.coerceIn(0f, 100f) }
        if (values.isEmpty()) return@Canvas
        val step = if (values.size == 1) size.width else size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { index, (left, right) ->
            drawLine(
                color = Color(0xFFD33A7C),
                start = Offset(index * step, size.height * (1f - left / 100f)),
                end = Offset((index + 1) * step, size.height * (1f - right / 100f)),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
        if (values.size == 1) drawCircle(Color(0xFFD33A7C), 5f, Offset(size.width / 2, size.height * (1f - values.first() / 100f)))
    }
    B0(text = "${points.first().probabilityPercent}% → ${points.last().probabilityPercent}%", color = white50)
}

@Composable
private fun Positions(
    snapshot: PolkamarktSnapshot,
    state: PolkamarktUiState,
    onSelectMarket: (String?) -> Unit,
    onClaimTrader: (String) -> Unit,
    onClaimCreator: (String) -> Unit
) {
    if (snapshot.positions.isEmpty() && snapshot.claimable.isEmpty()) {
        B0(text = snapshot.account.reason ?: "No Polkamarkt positions for this SORA account.", color = white50)
    }
    snapshot.positions.forEach { position ->
        Column(
            modifier = Modifier.fillMaxWidth().background(white08, RoundedCornerShape(12.dp))
                .clickable { onSelectMarket(position.marketId) }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            H3(text = position.marketTitle ?: "Market #${position.marketId}")
            B0(text = "${position.outcome ?: "Position"} · ${position.shares ?: position.yesShares ?: "0"} shares", color = white50)
        }
    }
    snapshot.claimable.forEach { claim -> ClaimCard(claim, snapshot, state, onClaimTrader, onClaimCreator) }
}

@Composable
private fun ClaimCard(
    claim: PolkamarktClaimable,
    snapshot: PolkamarktSnapshot,
    state: PolkamarktUiState,
    onClaimTrader: (String) -> Unit,
    onClaimCreator: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(white08, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        H3(text = "Claims · Market #${claim.marketId}")
        val enabled = state.disclaimerAccepted && state.mutationsEnabled && snapshot.account.signable && snapshot.account.hasXorForFees && !state.mutating
        val trader = claim.claimablePayout ?: claim.traderPayout
        if (trader.toBigIntegerOrNull()?.signum() == 1) {
            B0(text = "Trader payout ${trader} codec KUSD", color = white50)
            GrayButton(text = "Claim trader payout", enabled = enabled && snapshot.capabilities.claimMarket) { onClaimTrader(claim.marketId) }
        }
        if (claim.isCreator && claim.creatorFees.toBigIntegerOrNull()?.signum() == 1) {
            B0(text = "Creator fees ${claim.creatorFees} codec KUSD", color = white50)
            GrayButton(text = "Claim creator fees", enabled = enabled && snapshot.capabilities.claimCreatorFees) { onClaimCreator(claim.marketId) }
        }
    }
}
