package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_polkaswap_api.R
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.parcelize.Parcelize

@Parcelize
data class SwapDetailsViewState(
    val fromTokenId: String,
    val toTokenId: String,
    val fromTokenName: String,
    val toTokenName: String,
    val fromTokenImage: String?,
    val toTokenImage: String?,
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val toTokenMinReceived: String,
    val toFiatMinReceived: String,
    val networkFee: NetworkFee,
    val liquidityProviderFee: NetworkFee,
    val fromTokenOnToToken: String,
    val toTokenOnFromToken: String,
    val minmaxTitle: String
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
    val fee: BigDecimal
)

fun QuoteResponse.toModel(chainAsset: Chain.Asset): SwapQuote {
    return SwapQuote(chainAsset.amountFromPlanks(this.amount), chainAsset.amountFromPlanks(this.fee))
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
            fromAmount = amount.formatTokenAmount(fromAsset.token.configuration)
            toAmount = details.amount.formatTokenAmount(toAsset.token.configuration)

            minMaxTitle = resourceManager.getString(R.string.common_min_received)
            minMaxAmount = details.minMax.formatTokenAmount(toAsset.token.configuration)
            minMaxFiat = toAsset.token.fiatAmount(details.minMax)?.formatAsCurrency(toAsset.token.fiatSymbol)
        }
        WithDesired.OUTPUT -> {
            fromAmount = details.amount.formatTokenAmount(fromAsset.token.configuration)
            toAmount = amount.formatTokenAmount(toAsset.token.configuration)

            minMaxTitle = resourceManager.getString(R.string.polkaswap_maximum_sold)
            minMaxAmount = details.minMax.formatTokenAmount(fromAsset.token.configuration)
            minMaxFiat = fromAsset.token.fiatAmount(details.minMax)?.formatAsCurrency(fromAsset.token.fiatSymbol)
        }
        else -> Unit
    }
    val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
    val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

    return SwapDetailsViewState(
        fromTokenId = tokenFromId,
        toTokenId = tokenToId,
        fromTokenName = fromAsset.token.configuration.symbolToShow.uppercase(),
        toTokenName = toAsset.token.configuration.symbolToShow.uppercase(),
        fromTokenImage = fromAsset.token.configuration.iconUrl,
        toTokenImage = toAsset.token.configuration.iconUrl,
        fromTokenAmount = fromAmount,
        toTokenAmount = toAmount,
        minmaxTitle = minMaxTitle.orEmpty(),
        toTokenMinReceived = minMaxAmount.orEmpty(),
        toFiatMinReceived = minMaxFiat.orEmpty(),
        fromTokenOnToToken = details.fromTokenOnToToken.format(),
        toTokenOnFromToken = details.toTokenOnFromToken.format(),
        networkFee = SwapDetailsViewState.NetworkFee(
            details.feeAsset.token.configuration.symbolToShow.uppercase(),
            details.networkFee.formatTokenAmount(details.feeAsset.token.configuration),
            details.feeAsset.token.fiatAmount(details.networkFee)?.formatAsCurrency(details.feeAsset.token.fiatSymbol)
        ),
        liquidityProviderFee = SwapDetailsViewState.NetworkFee(
            details.feeAsset.token.configuration.symbolToShow.uppercase(),
            details.liquidityProviderFee.formatTokenAmount(details.feeAsset.token.configuration),
            details.feeAsset.token.fiatAmount(details.liquidityProviderFee)?.formatAsCurrency(details.feeAsset.token.fiatSymbol)
        )
    )
}
