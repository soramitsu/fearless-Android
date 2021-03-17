package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigInteger

data class NetworkInfo(
    val lockupPeriodInDays: Int,
    val minimumStake: BigInteger,
    val totalStake: BigInteger,
    val nominatorsCount: Int,
)
