package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal
import java.math.BigInteger

class Token(
    val configuration: Asset,
    val fiatRate: BigDecimal?,
    val fiatSymbol: String?,
    val recentRateChange: BigDecimal?
) {

    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = fiatRate?.multiply(tokenAmount)
}

fun Token.amountFromPlanks(amountInPlanks: BigInteger) = configuration.amountFromPlanks(amountInPlanks)

fun Token.planksFromAmount(amount: BigDecimal): BigInteger = configuration.planksFromAmount(amount)

fun Asset.amountFromPlanks(amountInPlanks: BigInteger) = amountInPlanks.toBigDecimal(scale = precision)

fun Asset.planksFromAmount(amount: BigDecimal): BigInteger = amount.scaleByPowerOfTen(precision).toBigInteger()
