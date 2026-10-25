package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.cache.CachedFiatCurrencyIdentity
import jp.co.soramitsu.coredb.model.cache.CachedNumericState
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceRead
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.expectedFiatPriceId

/** Trust, catalog key and persisted currency must all agree before a quote carries monetary authority. */
fun configuredFiatToken(
    configuration: Asset,
    price: CachedTokenPriceRead?,
    hasTrustedPrice: Boolean,
    context: FiatValuationContext
): Token {
    val admitted = price.takeIf { hasTrustedPrice }
    val issue = when {
        !context.isValid -> "invalid-selected-fiat"
        admitted == null -> null
        admitted.priceId != expectedFiatPriceId(configuration, context.currencyId) -> "cached-price-identity-mismatch"
        !admitted.fiatCurrencyIdentity.matches(context.currencyId) -> "cached-price-currency-mismatch"
        else -> null
    }
    return Token(
        configuration = configuration,
        fiatRateInput = issue?.let { AssetAmountState.Unavailable(it) }
            ?: admitted?.fiatRate?.toQuoteAmountState() ?: AssetAmountState.Missing,
        recentRateChangeInput = issue?.let { AssetAmountState.Unavailable(it) }
            ?: admitted?.recentRateChange?.toQuoteAmountState() ?: AssetAmountState.Missing,
        fiatContext = context,
        quoteCurrencyId = (admitted?.fiatCurrencyIdentity as? CachedFiatCurrencyIdentity.Known)?.value,
        quotePriceId = admitted?.priceId
    )
}

private fun <T> CachedNumericState<T>.toQuoteAmountState(): AssetAmountState<T> = when (this) {
    CachedNumericState.Missing -> AssetAmountState.Missing
    is CachedNumericState.Known -> AssetAmountState.Known(value)
    is CachedNumericState.Unavailable -> AssetAmountState.Unavailable("cached-${issue.reason.name}")
}
