package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktHistoryPoint
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktPosition
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktTrade
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import java.math.BigDecimal
import java.math.BigInteger

internal const val POLKAMARKT_PRECISION = 18
private const val HEX_RADIX = 16
private const val BASIS_POINTS_PER_ONE = 10_000
internal const val KUSD_ASSET_ID = "0x02000c0000000000000000000000000000000000000000000000000000000000"
internal const val XOR_ASSET_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
internal val nonNegativeInteger = Regex("^(0|[1-9]\\d*)$")
private val nonNegativeDecimal = Regex("^(0|[1-9]\\d*)(\\.\\d+)?$")

internal fun normalizeDecimal(value: String?, fallback: String = "0"): String {
    val normalized = value?.replace(",", "")?.trim().orEmpty()
    return normalized.takeIf(nonNegativeDecimal::matches) ?: fallback
}

internal fun normalizeInteger(value: String?): String? {
    val normalized = value?.replace(",", "")?.trim().orEmpty()
    return when {
        nonNegativeInteger.matches(normalized) -> normalized
        normalized.startsWith("0x", ignoreCase = true) -> runCatching {
            BigInteger(normalized.drop(2), HEX_RADIX).toString()
        }.getOrNull()
        else -> null
    }
}

internal fun toCodecAmount(value: String, precision: Int = POLKAMARKT_PRECISION): BigInteger {
    val normalized = normalizeDecimal(value, "")
    require(normalized.isNotEmpty()) { "Enter a non-negative decimal amount." }
    val parts = normalized.split('.', limit = 2)
    val fraction = parts.getOrElse(1) { "" }
    require(fraction.length <= precision) { "Amount supports at most $precision decimal places." }
    return BigInteger(parts[0] + fraction.padEnd(precision, '0'))
}

internal fun fromCodecAmount(value: String, precision: Int = POLKAMARKT_PRECISION): String {
    val integer = normalizeInteger(value) ?: return "0"
    val digits = integer.padStart(precision + 1, '0')
    val whole = digits.dropLast(precision).trimStart('0').ifEmpty { "0" }
    val fraction = digits.takeLast(precision).trimEnd('0')
    return if (fraction.isEmpty()) whole else "$whole.$fraction"
}

internal fun minimumAfterSlippage(value: BigInteger, slippageBps: Int = 100): BigInteger {
    require(slippageBps in 0 until BASIS_POINTS_PER_ONE)
    return value.multiply(BigInteger.valueOf((BASIS_POINTS_PER_ONE - slippageBps).toLong()))
        .divide(BigInteger.valueOf(BASIS_POINTS_PER_ONE.toLong()))
}

internal fun deriveStatus(
    status: String?,
    closeBlock: String?,
    currentBlock: String
): PolkamarktDisplayStatus {
    return when (status.orEmpty().replace(Regex("[_\\s-]"), "").lowercase()) {
        "resolved" -> PolkamarktDisplayStatus.Resolved
        "cancelled", "canceled" -> PolkamarktDisplayStatus.Cancelled
        "locked", "earlyreportlocked" -> PolkamarktDisplayStatus.Locked
        "closed" -> PolkamarktDisplayStatus.Closed
        else -> {
            val close = normalizeInteger(closeBlock)?.toBigIntegerOrNull()
            val current = normalizeInteger(currentBlock)?.toBigIntegerOrNull() ?: BigInteger.ZERO
            if (close != null && current >= close) PolkamarktDisplayStatus.Closed else PolkamarktDisplayStatus.Open
        }
    }
}

private fun JsonElement?.stringOrNull(): String? = this
    ?.takeUnless { it.isJsonNull }
    ?.let { runCatching { it.asString.trim() }.getOrNull() }
    ?.takeIf(String::isNotEmpty)

internal fun JsonObject.firstString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> get(key).stringOrNull() }

private fun JsonObject.nodes(key: String): List<JsonObject> {
    val value = get(key) ?: return emptyList()
    val array = when {
        value.isJsonArray -> value.asJsonArray
        value.isJsonObject -> value.asJsonObject.getAsJsonArray("edges") ?: JsonArray()
        else -> JsonArray()
    }
    return array.mapNotNull { item ->
        val candidate = if (item.isJsonObject && item.asJsonObject.has("node")) item.asJsonObject.get("node") else item
        candidate.takeIf(JsonElement::isJsonObject)?.asJsonObject
    }
}

private fun probability(value: String?): String? {
    val decimal = normalizeDecimal(value, "").takeIf(String::isNotEmpty)?.toBigDecimalOrNull() ?: return null
    val percent = if (decimal <= BigDecimal.ONE) decimal.movePointRight(2) else decimal
    return percent.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("100") }
        ?.stripTrailingZeros()?.toPlainString()
}

internal fun parseIndexedMarkets(root: JsonObject, currentBlock: String): List<PolkamarktMarket> =
    root.nodes("markets").mapNotNull { market ->
        val id = normalizeInteger(market.firstString("marketId", "id")) ?: return@mapNotNull null
        val title = market.firstString("title") ?: return@mapNotNull null
        val closeBlock = normalizeInteger(market.firstString("closeBlock"))
        PolkamarktMarket(
            id = id,
            conditionId = normalizeInteger(market.firstString("conditionId")),
            creator = market.firstString("creator"),
            title = title,
            description = market.firstString("description")
                ?: "Review the SORA oracle and resolution source before trading.",
            category = market.firstString("category") ?: "Other",
            oracle = market.firstString("oracle"),
            resolutionSource = market.firstString("resolutionSource"),
            closeBlock = closeBlock,
            runtimeStatus = market.firstString("status"),
            mechanism = market.firstString("mechanism"),
            liquidityUsd = normalizeDecimal(market.firstString("liquidityUsd", "liquidityUSD", "liquidity", "dpmCollateral")),
            volumeUsd = normalizeDecimal(market.firstString("volumeUsd", "volumeUSD", "volume", "marketVolume")),
            probabilityPercent = probability(market.firstString("probability", "priceYes")),
            displayStatus = deriveStatus(market.firstString("status"), closeBlock, currentBlock),
            runtimeOnly = false
        )
    }

internal fun mergeMarkets(indexed: List<PolkamarktMarket>, runtime: List<PolkamarktMarket>): List<PolkamarktMarket> {
    val byId = indexed.associateByTo(linkedMapOf(), PolkamarktMarket::id)
    runtime.forEach { chainMarket ->
        val indexedMarket = byId[chainMarket.id]
        byId[chainMarket.id] = indexedMarket?.copy(
            closeBlock = chainMarket.closeBlock ?: indexedMarket.closeBlock,
            runtimeStatus = chainMarket.runtimeStatus ?: indexedMarket.runtimeStatus,
            mechanism = chainMarket.mechanism ?: indexedMarket.mechanism,
            displayStatus = chainMarket.displayStatus,
            // `runtimeOnly` doubles as the runtime-authority marker. A market may retain its
            // richer indexer title/history, but mutations are allowed only after an exact
            // runtime market with the same id has been merged into it.
            runtimeOnly = true
        ) ?: chainMarket
    }
    val statusRank = listOf(
        PolkamarktDisplayStatus.Open,
        PolkamarktDisplayStatus.Locked,
        PolkamarktDisplayStatus.Closed,
        PolkamarktDisplayStatus.Resolved,
        PolkamarktDisplayStatus.Cancelled
    ).withIndex().associate { it.value to it.index }
    return byId.values.sortedWith(
        compareBy<PolkamarktMarket> { statusRank.getValue(it.displayStatus) }
            .thenByDescending { normalizeDecimal(it.volumeUsd).toBigDecimal() }
            .thenByDescending { it.id.toBigInteger() }
    )
}

/** Exact, human-decimal balances captured immediately before a mutation is built. */
internal data class PolkamarktExecutionBalances(
    val kusd: String,
    val xor: String,
    val yesShares: String = "0",
    val noShares: String = "0"
)

/**
 * Final trade boundary. UI capability flags and a previously displayed quote are deliberately
 * not inputs: the caller must supply the newly fetched runtime market, runtime quote, exact
 * AssetKey-bound balances, and the freshly estimated XOR fee contained in [freshQuote].
 */
internal fun validatePolkamarktTradeExecution(
    request: PolkamarktMutation.Trade,
    market: PolkamarktMarket?,
    capabilities: PolkamarktRuntimeCapabilities,
    freshQuote: PolkamarktQuote,
    balances: PolkamarktExecutionBalances
) {
    require(market != null && market.runtimeOnly) {
        "The selected market is not authoritative in the connected SORA runtime."
    }
    require(market.displayStatus == PolkamarktDisplayStatus.Open) {
        "This market is no longer open. Refresh before trading."
    }
    require(capabilities.marketState) { "Live market state is unavailable." }
    require(
        if (request.mode == PolkamarktTradeMode.Buy) {
            capabilities.buy && capabilities.quoteBuy
        } else {
            capabilities.sell && capabilities.quoteSell
        }
    ) { "Connected SORA runtime does not expose this Polkamarkt trade." }

    val amount = request.amount.positiveDecimal("Enter a positive trade amount.")
    val submittedMinimum = request.minimumResult.positiveDecimal("Refresh the market quote before confirming.")
    val quotedAmount = freshQuote.amount.positiveDecimal("SORA returned an invalid quote amount.")
    val quotedMinimum = freshQuote.minimumResult.positiveDecimal("SORA returned an invalid minimum result.")
    val quotedResult = freshQuote.resultAmount.positiveDecimal("SORA returned an invalid quote result.")
    val marketFee = freshQuote.feeAmount.nonNegativeDecimal("SORA returned an invalid market fee.")
    val networkFee = freshQuote.networkFee.positiveDecimal("Refresh the current XOR network fee.")
    val xor = balances.xor.nonNegativeDecimal("The exact XOR balance is unavailable.")

    require(
        freshQuote.marketId == request.marketId &&
            freshQuote.mode == request.mode &&
            freshQuote.outcome == request.outcome &&
            quotedAmount.compareTo(amount) == 0
    ) { "The market quote no longer matches this order. Refresh before confirming." }
    require(submittedMinimum.compareTo(quotedMinimum) == 0 && quotedMinimum <= quotedResult) {
        "The market quote changed. Review the new minimum before confirming."
    }
    require(xor >= networkFee) { "Add enough XOR to pay the current SORA network fee." }

    if (request.mode == PolkamarktTradeMode.Buy) {
        val kusd = balances.kusd.nonNegativeDecimal("The exact KUSD balance is unavailable.")
        require(kusd >= amount + marketFee) {
            "Add enough KUSD for the order and its current market fee."
        }
    } else {
        val shares = when (request.outcome) {
            jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome.Yes -> balances.yesShares
            jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome.No -> balances.noShares
        }.nonNegativeDecimal("The exact market share balance is unavailable.")
        require(shares >= amount) { "The order exceeds the available market shares." }
    }
}

internal fun validatePolkamarktClaimExecution(
    request: PolkamarktMutation,
    capabilities: PolkamarktRuntimeCapabilities,
    claimable: PolkamarktClaimable?,
    xorBalance: String,
    networkFee: String
) {
    require(request !is PolkamarktMutation.Trade)
    val xor = xorBalance.nonNegativeDecimal("The exact XOR balance is unavailable.")
    val fee = networkFee.positiveDecimal("Refresh the current XOR network fee.")
    require(xor >= fee) { "Add enough XOR to pay the current SORA network fee." }
    require(claimable != null && claimable.marketId == request.marketId) {
        "This market has no authoritative claim for the selected SORA account."
    }

    when (request) {
        is PolkamarktMutation.ClaimMarket -> {
            require(capabilities.claimMarket) { "Trader claims are unavailable on this runtime." }
            val payout = listOfNotNull(claimable.claimablePayout, claimable.traderPayout)
                .mapNotNull(::normalizeInteger)
                .map(String::toBigInteger)
                .maxOrNull() ?: BigInteger.ZERO
            require(payout > BigInteger.ZERO) { "There is no trader payout to claim." }
        }
        is PolkamarktMutation.ClaimCreatorFees -> {
            require(capabilities.claimCreatorFees) { "Creator claims are unavailable on this runtime." }
            val creatorFees = normalizeInteger(claimable.creatorFees)?.toBigIntegerOrNull() ?: BigInteger.ZERO
            require(claimable.isCreator && creatorFees > BigInteger.ZERO) {
                "There are no creator fees to claim."
            }
        }
        is PolkamarktMutation.Trade -> error("Trade validation uses validatePolkamarktTradeExecution().")
    }
}

private fun String.positiveDecimal(message: String): BigDecimal =
    nonNegativeDecimal(message).also { require(it > BigDecimal.ZERO) { message } }

private fun String.nonNegativeDecimal(message: String): BigDecimal {
    val normalized = normalizeDecimal(this, "")
    return normalized.toBigDecimalOrNull() ?: throw IllegalArgumentException(message)
}

internal fun parseHistory(root: JsonObject): List<PolkamarktHistoryPoint> =
    root.nodes("marketSnapshots").mapNotNull { point ->
    val id = point.firstString("id") ?: return@mapNotNull null
    val marketId = normalizeInteger(point.firstString("marketId")) ?: return@mapNotNull null
    val probability = probability(point.firstString("probability", "priceYes")) ?: return@mapNotNull null
    PolkamarktHistoryPoint(
        id = id,
        marketId = marketId,
        timestamp = normalizeInteger(point.firstString("timestamp")),
        blockHeight = normalizeInteger(point.firstString("blockHeight")),
        probabilityPercent = probability,
        priceYes = normalizeDecimal(point.firstString("priceYes"), "").ifEmpty { null },
        priceNo = normalizeDecimal(point.firstString("priceNo"), "").ifEmpty { null },
        liquidityUsd = normalizeDecimal(point.firstString("liquidityUsd", "liquidityUSD"), "").ifEmpty { null },
        volumeUsd = normalizeDecimal(point.firstString("volumeUsd", "volumeUSD"), "").ifEmpty { null }
    )
}

internal data class ParsedActivity(
    val positions: List<PolkamarktPosition>,
    val trades: List<PolkamarktTrade>
)

internal fun parseActivity(root: JsonObject): ParsedActivity {
    val positionNodes = root.nodes("accountPositions").ifEmpty { root.nodes("positions") }
    val positions = positionNodes.mapNotNull { position ->
        val market = position.getAsJsonObject("market") ?: JsonObject()
        val marketId = normalizeInteger(position.firstString("marketId") ?: market.firstString("marketId", "id"))
            ?: return@mapNotNull null
        PolkamarktPosition(
            id = position.firstString("id") ?: marketId,
            marketId = marketId,
            marketTitle = position.firstString("marketTitle") ?: market.firstString("title"),
            outcome = position.firstString("outcome")?.uppercase(),
            shares = normalizeDecimal(position.firstString("shares", "sharesAmount"), "").ifEmpty { null },
            yesShares = normalizeDecimal(position.firstString("yesShares"), "").ifEmpty { null },
            noShares = normalizeDecimal(position.firstString("noShares"), "").ifEmpty { null },
            netCollateralPaid = normalizeDecimal(position.firstString("netCollateralPaid"), "").ifEmpty { null },
            claimablePayout = normalizeDecimal(position.firstString("claimablePayout", "claimablePayoutUsd"), "").ifEmpty { null },
            isCreator = position.get("isCreator")?.let { runCatching { it.asBoolean }.getOrNull() } ?: false,
            status = position.firstString("status") ?: market.firstString("status")
        )
    }
    val tradeNodes = root.nodes("accountTrades").ifEmpty { root.nodes("trades") }
    val trades = tradeNodes.mapNotNull { trade ->
        val marketId = normalizeInteger(trade.firstString("marketId")) ?: return@mapNotNull null
        PolkamarktTrade(
            id = trade.firstString("id", "extrinsicHash") ?: marketId,
            marketId = marketId,
            side = trade.firstString("side", "action")?.lowercase(),
            outcome = trade.firstString("outcome")?.uppercase(),
            collateral = normalizeDecimal(trade.firstString("collateral", "collateralUsd", "collateralAmount", "collateralAmountUsd"), "").ifEmpty {
                null
            },
            sharesIn = normalizeDecimal(trade.firstString("sharesIn"), "").ifEmpty { null },
            sharesOut = normalizeDecimal(trade.firstString("sharesOut"), "").ifEmpty { null },
            fee = normalizeDecimal(trade.firstString("fee", "feeAmount", "feeUsd"), "").ifEmpty { null },
            blockNumber = normalizeInteger(trade.firstString("blockNumber", "blockHeight")),
            extrinsicHash = trade.firstString("extrinsicHash", "txHash")
        )
    }
    return ParsedActivity(positions, trades)
}

internal fun parseClaimable(
    root: JsonObject,
    account: String,
    marketId: String
): PolkamarktClaimable? {
    if (root.entrySet().isEmpty()) return null
    return PolkamarktClaimable(
        marketId = normalizeInteger(root.firstString("marketId")) ?: marketId,
        account = root.firstString("account") ?: account,
        status = root.firstString("status").orEmpty(),
        resolutionOutcome = root.firstString("resolutionOutcome"),
        yesShares = normalizeInteger(root.firstString("yesShares")) ?: "0",
        noShares = normalizeInteger(root.firstString("noShares")) ?: "0",
        netCollateralPaid = normalizeInteger(root.firstString("netCollateralPaid")) ?: "0",
        traderPayout = normalizeInteger(root.firstString("traderPayout")) ?: "0",
        claimablePayout = root.firstString("claimablePayout", "claimable_payout")?.let(::normalizeInteger),
        creatorFees = normalizeInteger(root.firstString("creatorFees")) ?: "0",
        isCreator = root.get("isCreator")?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
    )
}
