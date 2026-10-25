package jp.co.soramitsu.polkamarkt.api

enum class PolkamarktOutcome { Yes, No }
enum class PolkamarktTradeMode { Buy, Sell }
enum class PolkamarktDisplayStatus { Open, Closed, Resolved, Cancelled, Locked }

data class PolkamarktMarket(
    val id: String,
    val conditionId: String? = null,
    val creator: String? = null,
    val title: String,
    val description: String,
    val category: String,
    val oracle: String? = null,
    val resolutionSource: String? = null,
    val closeBlock: String? = null,
    val runtimeStatus: String? = null,
    val mechanism: String? = null,
    val liquidityUsd: String = "0",
    val volumeUsd: String = "0",
    val probabilityPercent: String? = null,
    val displayStatus: PolkamarktDisplayStatus,
    val runtimeOnly: Boolean = false
)

data class PolkamarktHistoryPoint(
    val id: String,
    val marketId: String,
    val timestamp: String? = null,
    val blockHeight: String? = null,
    val probabilityPercent: String,
    val priceYes: String? = null,
    val priceNo: String? = null,
    val liquidityUsd: String? = null,
    val volumeUsd: String? = null
)

data class PolkamarktPosition(
    val id: String,
    val marketId: String,
    val marketTitle: String? = null,
    val outcome: String? = null,
    val shares: String? = null,
    val yesShares: String? = null,
    val noShares: String? = null,
    val netCollateralPaid: String? = null,
    val claimablePayout: String? = null,
    val isCreator: Boolean = false,
    val status: String? = null
)

data class PolkamarktTrade(
    val id: String,
    val marketId: String,
    val side: String? = null,
    val outcome: String? = null,
    val collateral: String? = null,
    val sharesIn: String? = null,
    val sharesOut: String? = null,
    val fee: String? = null,
    val blockNumber: String? = null,
    val extrinsicHash: String? = null
)

data class PolkamarktClaimable(
    val marketId: String,
    val account: String,
    val status: String,
    val resolutionOutcome: String? = null,
    val yesShares: String = "0",
    val noShares: String = "0",
    val netCollateralPaid: String = "0",
    val traderPayout: String = "0",
    val claimablePayout: String? = null,
    val creatorFees: String = "0",
    val isCreator: Boolean = false
)

data class PolkamarktRuntimeCapabilities(
    val browse: Boolean,
    val quoteBuy: Boolean,
    val quoteSell: Boolean,
    val marketState: Boolean,
    val buy: Boolean,
    val sell: Boolean,
    val claimMarket: Boolean,
    val claimCreatorFees: Boolean,
    val reasons: List<String> = emptyList()
)

data class PolkamarktAccountCapability(
    val address: String? = null,
    val signable: Boolean = false,
    val hasKusd: Boolean = false,
    val hasXorForFees: Boolean = false,
    val reason: String? = null
)

data class PolkamarktSnapshot(
    val currentBlock: String,
    val markets: List<PolkamarktMarket>,
    val history: List<PolkamarktHistoryPoint>,
    val positions: List<PolkamarktPosition>,
    val trades: List<PolkamarktTrade>,
    val claimable: List<PolkamarktClaimable>,
    val capabilities: PolkamarktRuntimeCapabilities,
    val account: PolkamarktAccountCapability,
    val indexerStale: Boolean,
    val warnings: List<String>
)

data class PolkamarktQuoteRequest(
    val marketId: String,
    val mode: PolkamarktTradeMode,
    val outcome: PolkamarktOutcome,
    val amount: String
)

data class PolkamarktQuote(
    val marketId: String,
    val mode: PolkamarktTradeMode,
    val outcome: PolkamarktOutcome,
    val amount: String,
    val feeAmount: String,
    val networkFee: String,
    val resultAmount: String,
    val minimumResult: String
)

sealed interface PolkamarktMutation {
    val marketId: String

    data class Trade(
        override val marketId: String,
        val mode: PolkamarktTradeMode,
        val outcome: PolkamarktOutcome,
        val amount: String,
        val minimumResult: String
    ) : PolkamarktMutation

    data class ClaimMarket(override val marketId: String) : PolkamarktMutation
    data class ClaimCreatorFees(override val marketId: String) : PolkamarktMutation
}

interface PolkamarktInteractor {
    suspend fun snapshot(marketId: String? = null): PolkamarktSnapshot
    suspend fun quote(request: PolkamarktQuoteRequest): PolkamarktQuote
    suspend fun mutate(request: PolkamarktMutation): Result<String>
}

interface PolkamarktRouter {
    fun openPolkamarkt(marketId: String? = null)
    fun openRiskDisclaimer()
    fun back()
}

object PolkamarktDeepLinks {
    const val MARKET = "fearless://defi/polkamarkt/{marketId}"
}
