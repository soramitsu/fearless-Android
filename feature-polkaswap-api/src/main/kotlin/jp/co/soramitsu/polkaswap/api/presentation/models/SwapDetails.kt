package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class SwapDetails(
    val fromTokenId: String,
    val toTokenId: String,
    val fromTokenName: String,
    val toTokenName: String,
    val fromTokenImage: String?,
    val toTokenImage: String?,
    val fromTokenAmount: BigDecimal,
    val toTokenAmount: BigDecimal,
    val toTokenMinReceived: BigDecimal,
    val toFiatMinReceived: String,
    val networkFee: NetworkFee
) : Parcelable {

    @IgnoredOnParcel
    val fromTokenOnToToken = fromTokenAmount.divide(toTokenAmount, AmountScale, RoundingMode.HALF_UP)

    @IgnoredOnParcel
    val toTokenOnFromToken = toTokenAmount.divide(fromTokenAmount, AmountScale, RoundingMode.HALF_UP)

    @Parcelize
    data class NetworkFee(
        val tokenName: String,
        val tokenAmount: BigDecimal,
        val fiatAmount: String
    ) : Parcelable

    companion object {
        private const val AmountScale = 6
    }
}
