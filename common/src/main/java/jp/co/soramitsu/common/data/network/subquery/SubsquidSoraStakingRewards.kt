package jp.co.soramitsu.common.data.network.subquery

import java.math.BigDecimal

data class SubsquidSoraStakingRewards (
    val stakingRewards: List<RewardItem>
)

data class RewardItem(
    val id: String,
    val amount: BigDecimal,
    val timestamp: Long
)