package jp.co.soramitsu.polkaswap.api.domain.models

import android.os.Parcelable
import java.math.BigDecimal
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.parcelize.Parcelize

data class BasicPoolData(
//    val baseToken: Token,
//    val targetToken: Token,
    val baseToken: Asset,
    val targetToken: Asset?,
    val baseReserves: BigDecimal,
    val targetReserves: BigDecimal,
    val totalIssuance: BigDecimal,
    val reserveAccount: String,
    val sbapy: Double?,
) {
    val fiatSymbol = "baseToken.fiatSymbol"
    val tvl: BigDecimal?
        get() = baseToken.token.fiatRate?.times(BigDecimal(2))?.multiply(baseReserves)
}

@Parcelize
data class Token(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isHidable: Boolean,
    val iconFile: String?,
    val fiatPrice: Double?,
    val fiatPriceChange: Double?,
    val fiatSymbol: String?,
) : Parcelable {

//    fun printBalance(
//        balance: BigDecimal,
//        nf: NumbersFormatter,
//        precision: Int = this.precision
//    ): String =
//        String.format("%s %s", nf.formatBigDecimal(balance, precision), symbol)

//    fun printBalanceWithConstrainedLength(
//        balance: BigDecimal,
//        nf: NumbersFormatter,
//        length: Int = 8
//    ): String {
//        val integerLength = balance.toBigInteger().toString().length
//        var newPrecision = length - integerLength
//        if (newPrecision <= 0) {
//            newPrecision = 1
//        }
//
//        return printBalance(balance, nf, newPrecision)
//    }
}
