package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.runtime.model.QuoteResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
