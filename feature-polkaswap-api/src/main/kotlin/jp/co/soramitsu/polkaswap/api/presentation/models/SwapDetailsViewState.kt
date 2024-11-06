package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
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
    val route: String?,
    val fromChainId: String?,
    val toChainId: String?,
    val fromChainIdImage: String?,
    val toChainIdImage: String?,
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
