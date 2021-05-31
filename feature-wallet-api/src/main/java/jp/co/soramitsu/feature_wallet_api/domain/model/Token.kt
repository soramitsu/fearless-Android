package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.core.model.Node
import java.math.BigDecimal
import java.math.BigInteger

private const val DEFAULT_MANTISSA = 12

class Token(
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?,
    val type: Type
) {

    fun fiatAmount(tokenAmount: BigDecimal): BigDecimal? = dollarRate?.multiply(tokenAmount)

    enum class Type(
        val displayName: String,
        val networkType: Node.NetworkType,
        val mantissa: Int = DEFAULT_MANTISSA
    ) {
        KSM("KSM", Node.NetworkType.KUSAMA),
        DOT("DOT", Node.NetworkType.POLKADOT, 10),
        WND("WND", Node.NetworkType.WESTEND),
        ROC("ROC", Node.NetworkType.ROCOCO);

        companion object {
            fun fromNetworkType(networkType: Node.NetworkType): Type {
                return when (networkType) {
                    Node.NetworkType.KUSAMA -> KSM
                    Node.NetworkType.POLKADOT -> DOT
                    Node.NetworkType.WESTEND -> WND
                    Node.NetworkType.ROCOCO -> ROC
                }
            }
        }
    }
}

fun Token.amountFromPlanks(amountInPlanks: BigInteger) = type.amountFromPlanks(amountInPlanks)

fun Token.planksFromAmount(amount: BigDecimal): BigInteger = type.planksFromAmount(amount)

fun Token.Type.amountFromPlanks(amountInPlanks: BigInteger) = amountInPlanks.toBigDecimal(scale = mantissa)

fun Token.Type.planksFromAmount(amount: BigDecimal): BigInteger = amount.scaleByPowerOfTen(mantissa).toBigInteger()
