package jp.co.soramitsu.staking.impl.data.model

import java.math.BigInteger

data class PoolMember(
    val poolId: BigInteger,
    // bonded amount
    val points: BigInteger,
    val lastRecordedRewardCounter: BigInteger,
    val unbondingEras: List<PoolUnbonding>
)
