package jp.co.soramitsu.polkaswap.api.domain.models

import java.math.BigDecimal

data class CommonUserPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData,
) {
    fun printFiat(): Pair<Double, Double>? = printFiatInternal(basic, user)
}

data class CommonPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData?,
) {
    fun printFiat(): Pair<Double, Double>? = printFiatInternal(basic, user)
}

data class UserPoolData(
//    val address: String?,
    val basePooled: BigDecimal,
    val targetPooled: BigDecimal,
    val poolShare: Double,
    val poolProvidersBalance: BigDecimal,
)

val List<CommonUserPoolData>.fiatSymbol: String
    get() {
        return getOrNull(0)?.basic?.fiatSymbol ?: "$" //OptionsProvider.fiatSymbol
    }

private fun printFiatInternal(basic: BasicPoolData, user: UserPoolData?): Pair<Double, Double>? {
    return null
//    if (user == null) return null
//    val f1 = user.basePooled.applyFiatRate(basic.baseToken.token.fiatRate)
//    val f2 = user.targetPooled.applyFiatRate(basic.targetToken?.token?.fiatRate)
//    if (f1 == null || f2 == null) return null
//    val change1 = basic.baseToken.fiatPriceChange ?: 0.0
//    val change2 = basic.targetToken.fiatPriceChange ?: 0.0
//    val price1 = basic.baseToken.fiatPrice ?: return null
//    val price2 = basic.targetToken.fiatPrice ?: return null
//    val newPoolFiat = f1 + f2
//    val oldPoolFiat = calcAmount(price1 / (1 + change1), user.basePooled) +
//        calcAmount(price2 / (1 + change2), user.targetPooled)
//    val changePool = fiatChange(oldPoolFiat, newPoolFiat)
//    return newPoolFiat to changePool
}

fun BasicPoolData.isFilterMatch(filter: String): Boolean {
    val t1 = targetToken?.token?.configuration?.name?.lowercase()?.contains(filter.lowercase()) == true ||
        targetToken?.token?.configuration?.symbol?.lowercase()?.contains(filter.lowercase())  == true ||
        targetToken?.token?.configuration?.id?.lowercase()?.contains(filter.lowercase()) == true
    val t2 = baseToken.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
        baseToken.token.configuration.symbol.lowercase().contains(filter.lowercase()) ||
        baseToken.token.configuration.id.lowercase().contains(filter.lowercase())
    return t1 || t2
}
