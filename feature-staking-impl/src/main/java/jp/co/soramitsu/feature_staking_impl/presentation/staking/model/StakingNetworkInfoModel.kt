package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import java.math.BigInteger

data class StakingNetworkInfoModel(
    val lockupPeriodInDays: Int,
    val minimumStake: BigInteger,
    val totalStake: BigInteger,
    val nominatorsCount: Int
)
