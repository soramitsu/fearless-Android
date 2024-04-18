package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.feature_polkaswap_api.R
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.parcelize.Parcelize
import jp.co.soramitsu.core.models.Asset as CoreAsset

@Parcelize
data class SwapDetailsViewState(
    val fromTokenId: String,
    val toTokenId: String,
    val fromTokenName: String,
    val toTokenName: String,
    val fromTokenImage: GradientIconState?,
    val toTokenImage: GradientIconState?,
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val toTokenMinReceived: String,
    val toFiatMinReceived: String,
    val fromTokenOnToToken: String,
    val toTokenOnFromToken: String,
    val minmaxTitle: String,
    val route: String?
) : Parcelable {

    @Parcelize
    data class NetworkFee(
        val tokenName: String,
        val tokenAmount: String,
        val fiatAmount: String?
    ) : Parcelable
}

data class SwapQuote(
    val amount: BigDecimal,
    val route: List<String>?
)

fun QuoteResponse.toModel(chainAsset: CoreAsset): SwapQuote {
    return SwapQuote(chainAsset.amountFromPlanks(this.amount), this.route)
}

fun detailsToViewState(
    resourceManager: ResourceManager,
    amount: BigDecimal,
    fromAsset: Asset,
    toAsset: Asset,
    details: SwapDetails,
    desired: WithDesired
): SwapDetailsViewState {
    var fromAmount = ""
    var toAmount = ""
    var minMaxTitle: String? = null
    var minMaxAmount: String? = null
    var minMaxFiat: String? = null

    when (desired) {
        WithDesired.INPUT -> {
            fromAmount = amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
            toAmount = details.amount.formatCryptoDetail(toAsset.token.configuration.symbol)

            minMaxTitle = resourceManager.getString(R.string.common_min_received)
            minMaxAmount = details.minMax.formatCryptoDetail(toAsset.token.configuration.symbol)
            minMaxFiat = toAsset.token.fiatAmount(details.minMax)?.formatFiat(toAsset.token.fiatSymbol)
        }
        WithDesired.OUTPUT -> {
            fromAmount = details.amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
            toAmount = amount.formatCryptoDetail(toAsset.token.configuration.symbol)

            minMaxTitle = resourceManager.getString(R.string.polkaswap_maximum_sold)
            minMaxAmount = details.minMax.formatCryptoDetail(fromAsset.token.configuration.symbol)
            minMaxFiat = fromAsset.token.fiatAmount(details.minMax)?.formatFiat(fromAsset.token.fiatSymbol)
        }
    }
    val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
    val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

    return SwapDetailsViewState(
        fromTokenId = tokenFromId,
        toTokenId = tokenToId,
        fromTokenName = fromAsset.token.configuration.symbol.uppercase(),
        toTokenName = toAsset.token.configuration.symbol.uppercase(),
        fromTokenImage = GradientIconState.Remote(fromAsset.token.configuration.iconUrl, fromAsset.token.configuration.color),
        toTokenImage = GradientIconState.Remote(toAsset.token.configuration.iconUrl, toAsset.token.configuration.color),
        fromTokenAmount = fromAmount,
        toTokenAmount = toAmount,
        minmaxTitle = minMaxTitle.orEmpty(),
        toTokenMinReceived = minMaxAmount.orEmpty(),
        toFiatMinReceived = minMaxFiat.orEmpty(),
        fromTokenOnToToken = details.fromTokenOnToToken.formatCryptoDetail(),
        toTokenOnFromToken = details.toTokenOnFromToken.formatCryptoDetail(),
        route = details.route
    )
}
