package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigDecimal

class StakingReward(
    val accountAddress: String,
    val type: Type,
    val blockNumber: Long,
    val extrinsicIndex: Int,
    val extrinsicHash: String,
    val moduleId: String,
    val eventIndex: String,
    val amount: BigDecimal,
    val blockTimestamp: Long,
) {

    enum class Type(val summingCoefficient: Int) {
        REWARD(1), SLASH(-1)
    }
}
