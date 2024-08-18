package jp.co.soramitsu.liquiditypools.domain.model

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal

data class BasicPoolData(
    val baseToken: Asset,
    val targetToken: Asset?,
    val baseReserves: BigDecimal,
    val targetReserves: BigDecimal,
    val totalIssuance: BigDecimal,
    val reserveAccount: String
) {
    fun getTvl(baseTokenFiatRate: BigDecimal?): BigDecimal? {
        return baseTokenFiatRate?.times(BigDecimal(2))?.multiply(baseReserves)
    }
}