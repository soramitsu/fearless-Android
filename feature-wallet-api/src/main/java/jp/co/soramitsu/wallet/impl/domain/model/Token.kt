package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal
import java.math.BigInteger

data class TokenPrice(
    val fiatRate: BigDecimal?,
    val fiatSymbol: String?,
    val recentRateChange: BigDecimal?
)

data class Token(
    val configuration: Asset,
    val fiatRateInput: AssetAmountState<BigDecimal>,
    val recentRateChangeInput: AssetAmountState<BigDecimal>,
    val fiatContext: FiatValuationContext? = null,
    val quoteCurrencyId: String? = null,
    val quotePriceId: String? = null
) {
    /** Compatibility values have no persisted currency authority, even when their symbol is familiar. */
    @Suppress("UNUSED_PARAMETER")
    constructor(
        configuration: Asset,
        fiatRateInput: AssetAmountState<BigDecimal>,
        fiatSymbol: String?,
        recentRateChangeInput: AssetAmountState<BigDecimal>
    ) : this(configuration, fiatRateInput, recentRateChangeInput)

    @Suppress("UNUSED_PARAMETER")
    constructor(
        configuration: Asset,
        fiatRate: BigDecimal?,
        fiatSymbol: String?,
        recentRateChange: BigDecimal?
    ) : this(configuration, fiatRate.asAssetAmountState(), recentRateChange.asAssetAmountState())

    val fiatSymbol: String? = fiatContext?.takeIf { it.isValid }?.displayUnit
    val fiatRateState = bindQuoteIdentity(validatePrice(fiatRateInput, nonNegative = true))
    val recentRateChangeState = bindQuoteIdentity(validatePrice(recentRateChangeInput, nonNegative = false))
    val fiatRate = fiatRateState.knownOrNull()
    val recentRateChange = recentRateChangeState.knownOrNull()

    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = fiatAmountState(AssetAmountState.Known(tokenAmount)).knownOrNull()

    fun fiatAmountState(tokenAmount: AssetAmountState<BigDecimal>): AssetAmountState<BigDecimal> =
        combineAssetAmountStates(validateFiatAmount(tokenAmount), fiatRateState, BigDecimal::multiply)

    /** Aggregators consume the unit together with the amount; numeric-only compatibility is not a total. */
    fun valuedAmount(tokenAmount: AssetAmountState<BigDecimal>): FiatAmount? =
        fiatContext?.let { FiatAmount(it.currencyId, fiatAmountState(tokenAmount)) }

    private fun bindQuoteIdentity(value: AssetAmountState<BigDecimal>): AssetAmountState<BigDecimal> = when {
        value !is AssetAmountState.Known -> value
        fiatContext == null || !fiatContext.isValid || quoteCurrencyId != fiatContext.currencyId ->
            AssetAmountState.Unavailable("unverified-quote-currency")
        quotePriceId == null || quotePriceId != expectedFiatPriceId(configuration, fiatContext.currencyId) ->
            AssetAmountState.Unavailable("unverified-quote-price")
        else -> value
    }
}

fun Token.amountFromPlanksState(amount: AssetAmountState<BigInteger>): AssetAmountState<BigDecimal> =
    if (configuration.precision !in 0..255) AssetAmountState.Unavailable("invalid-token-precision")
    else amount.mapAssetAmount { it.toBigDecimal(scale = configuration.precision) }

/** A total can carry when adding two independently bounded 4,096-digit cache fields. */
private fun validateFiatAmount(value: AssetAmountState<BigDecimal>): AssetAmountState<BigDecimal> =
    if (value is AssetAmountState.Known &&
        (value.value.scale() !in -4096..4096 || value.value.precision() > 4097)
    ) {
        AssetAmountState.Unavailable("fiat-amount-out-of-range")
    } else value

private fun validatePrice(value: AssetAmountState<BigDecimal>, nonNegative: Boolean): AssetAmountState<BigDecimal> =
    if (value is AssetAmountState.Known) {
        when {
            value.value.scale() !in -4096..4096 || value.value.precision() > 4096 ->
                AssetAmountState.Unavailable("price-out-of-range")
            nonNegative && value.value.signum() < 0 -> AssetAmountState.Unavailable("negative-fiat-rate")
            else -> value
        }
    } else value

fun Token.amountFromPlanks(amountInPlanks: BigInteger) = configuration.amountFromPlanks(amountInPlanks)

fun Token.planksFromAmount(amount: BigDecimal): BigInteger = configuration.planksFromAmount(amount)

fun Asset.amountFromPlanks(amountInPlanks: BigInteger) = amountInPlanks.toBigDecimal(scale = precision)

fun Asset.planksFromAmount(amount: BigDecimal): BigInteger = amount.scaleByPowerOfTen(precision).toBigInteger()
