package jp.co.soramitsu.polkaswap.api.domain.models

import java.math.BigDecimal
import jp.co.soramitsu.wallet.impl.domain.model.Asset

data class BasicPoolData(
    val baseToken: Asset,
    val targetToken: Asset?,
    val baseReserves: BigDecimal,
    val targetReserves: BigDecimal,
    val totalIssuance: BigDecimal,
    val reserveAccount: String,
    val sbapy: Double?,
) {
    val tvl: BigDecimal?
        get() = baseToken.token.fiatRate?.times(BigDecimal(2))?.multiply(baseReserves)
}