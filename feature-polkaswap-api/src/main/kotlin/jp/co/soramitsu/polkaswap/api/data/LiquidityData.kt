package jp.co.soramitsu.polkaswap.api.data

import java.math.BigDecimal

data class LiquidityData(
    val firstReserves: BigDecimal = BigDecimal.ZERO,
    val secondReserves: BigDecimal = BigDecimal.ZERO,
    val firstPooled: BigDecimal = BigDecimal.ZERO,
    val secondPooled: BigDecimal = BigDecimal.ZERO,
    val sbApy: Double? = null,
)
