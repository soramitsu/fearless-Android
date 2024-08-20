package jp.co.soramitsu.liquiditypools.impl.data

import java.math.BigInteger

class DemeterRewardTokenStorage(
    val token: String,
    val account: String,
    val farmsTotalMultiplier: BigInteger,
    val stakingTotalMultiplier: BigInteger,
    val tokenPerBlock: BigInteger,
    val farmsAllocation: BigInteger,
    val stakingAllocation: BigInteger,
    val teamAllocation: BigInteger,
)

class DemeterBasicStorage(
    val base: String,
    val pool: String,
    val reward: String,
    val multiplier: BigInteger,
    val isCore: Boolean,
    val isFarm: Boolean,
    val isRemoved: Boolean,
    val depositFee: BigInteger,
    val totalTokensInPool: BigInteger,
    val rewards: BigInteger,
    val rewardsToBeDistributed: BigInteger,
)

class DemeterStorage(
    val base: String,
    val pool: String,
    val reward: String,
    val farm: Boolean,
    val amount: BigInteger,
    val rewardAmount: BigInteger,
)
