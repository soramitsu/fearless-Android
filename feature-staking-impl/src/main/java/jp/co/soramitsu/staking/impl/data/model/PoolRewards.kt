package jp.co.soramitsu.staking.impl.data.model

import java.math.BigInteger

data class PoolRewards(
    val lastRecordedRewardCounter: BigInteger,
    val lastRecordedTotalPayouts: BigInteger,
    val totalRewardsClaimed: BigInteger
)
