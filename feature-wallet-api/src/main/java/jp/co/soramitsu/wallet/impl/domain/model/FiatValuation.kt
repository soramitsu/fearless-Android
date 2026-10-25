package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as CoreAsset

/** Captured selection; quote symbols and neighboring holdings never choose this unit. */
data class FiatValuationContext(val currencyId: String, val selectedMetadata: FiatCurrency? = null) {
    val isValid = currencyId.isNotBlank() && currencyId.length <= 4096 &&
        currencyId.toByteArray(Charsets.UTF_8).size <= 4096
    val displayUnit: String = selectedMetadata?.takeIf { it.id == currencyId }
        ?.symbol?.takeIf { it.isNotBlank() } ?: currencyId
}

/** A numeric amount retains its unit even after it leaves its originating token. */
data class FiatAmount(val currencyId: String, val amount: AssetAmountState<BigDecimal>)

/** Quantity remains independent of quote availability, including damaged or absent balances. */
data class FiatHoldingValuation(
    val quantity: AssetAmountState<BigDecimal>,
    val fiat: FiatAmount?
)

/** Ready result only. Loading must remain a separate snapshot state at the reader boundary. */
data class FiatPortfolioTotal(
    val context: FiatValuationContext,
    val knownSubtotal: BigDecimal?,
    val total: AssetAmountState<BigDecimal>,
    val pricedCount: Int,
    val unpricedFundedCount: Int,
    val unknownQuantityCount: Int
) {
    val isComplete: Boolean = total is AssetAmountState.Known
}

/** A completed empty/zero portfolio proves zero; unknown quantities and unpriced holdings do not. */
fun aggregateFiatHoldings(
    context: FiatValuationContext,
    holdings: List<FiatHoldingValuation>
): FiatPortfolioTotal {
    var subtotal = BigDecimal.ZERO
    var priced = 0
    var unpriced = 0
    var unknown = 0
    for (holding in holdings) {
        val quantity = holding.quantity
        if (quantity !is AssetAmountState.Known) {
            unknown++
            continue
        }
        if (quantity.value.signum() == 0) continue
        val value = holding.fiat
        val amount = value?.amount
        if (context.isValid && value?.currencyId == context.currencyId && amount is AssetAmountState.Known &&
            isBoundedFiatProduct(amount.value)
        ) {
            subtotal += amount.value
            priced++
        } else {
            unpriced++
        }
    }
    val complete = context.isValid && unpriced == 0 && unknown == 0
    val total = when {
        !context.isValid -> AssetAmountState.Unavailable("invalid-selected-fiat")
        complete -> AssetAmountState.Known(subtotal)
        else -> AssetAmountState.Unavailable("incomplete-fiat-total")
    }
    return FiatPortfolioTotal(
        context, subtotal.takeIf { complete || priced > 0 }, total, priced, unpriced, unknown
    )
}

/** Token multiplies an amount of at most 4,097 digits by a price of at most 4,096 digits. */
private fun isBoundedFiatProduct(value: BigDecimal): Boolean =
    value.scale() in -8192..8192 && value.precision() <= 8193

/** One current key, independent of the price pointer last written with a balance. */
fun expectedFiatPriceId(configuration: CoreAsset, selectedFiatId: String): String? =
    if (selectedFiatId == "usd" && configuration.priceProvider?.type == CoreAsset.PriceProviderType.Chainlink) {
        configuration.priceProvider?.id
    } else {
        configuration.priceId
    }

