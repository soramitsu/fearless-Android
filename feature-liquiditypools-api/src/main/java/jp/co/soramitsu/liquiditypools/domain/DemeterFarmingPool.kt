package jp.co.soramitsu.liquiditypools.domain

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal

data class DemeterFarmingPool(
    val tokenBase: Asset,
    val tokenTarget: Asset,
    val tokenReward: Asset,
    val apr: Double,
    val amount: BigDecimal,
    val amountReward: BigDecimal,
)

data class DemeterFarmingBasicPool(
    val tokenBase: Asset,
    val tokenTarget: Asset,
    val tokenReward: Asset,
    val apr: Double,
    val tvl: BigDecimal,
    val fee: Double,
)
