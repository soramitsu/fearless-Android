package jp.co.soramitsu.soracard.api.presentation.models

import android.net.Uri
import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

data class CommonUserPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData,
)

data class BasicPoolData(
    val baseToken: Token,
    val targetToken: Token,
    val baseReserves: BigDecimal,
    val targetReserves: BigDecimal,
    val totalIssuance: BigDecimal,
    val reserveAccount: String,
    val sbapy: Double?,
) {
    val fiatSymbol = baseToken.fiatSymbol
}

@Parcelize
data class Token(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isHidable: Boolean,
    val iconFile: Uri?,
    val fiatPrice: Double?,
    val fiatPriceChange: Double?,
    val fiatSymbol: String?,
) : Parcelable

data class UserPoolData(
    val basePooled: BigDecimal,
    val targetPooled: BigDecimal,
    val poolShare: Double,
    val poolProvidersBalance: BigDecimal,
    val favorite: Boolean,
    val sort: Int,
)
