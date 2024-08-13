package jp.co.soramitsu.polkaswap.api.domain.models

import java.math.BigDecimal

data class CommonUserPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData,
)

data class CommonPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData?,
)

data class UserPoolData(
//    val address: String?,
    val basePooled: BigDecimal,
    val targetPooled: BigDecimal,
    val poolShare: Double,
    val poolProvidersBalance: BigDecimal,
)

fun BasicPoolData.isFilterMatch(filter: String): Boolean {
    val t1 =
//        targetToken?.token?.configuration?.name?.lowercase()?.contains(filter.lowercase()) == true ||
        targetToken?.symbol?.lowercase()?.contains(filter.lowercase())  == true ||
        targetToken?.currencyId?.lowercase()?.contains(filter.lowercase()) == true
    val t2 =
//        baseToken.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
        baseToken.symbol.lowercase().contains(filter.lowercase()) ||
        baseToken.currencyId?.lowercase()?.contains(filter.lowercase()) == true
    return t1 || t2
}
