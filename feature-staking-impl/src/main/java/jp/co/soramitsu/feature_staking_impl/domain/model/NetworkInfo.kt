package jp.co.soramitsu.feature_staking_impl.domain.model

import java.math.BigInteger

sealed class NetworkInfoState {

    object Loading : NetworkInfoState()

    data class Loaded(val networkInfo: NetworkInfo) : NetworkInfoState()
}

data class NetworkInfo(
    val lockupPeriodInDays: Int,
    val minimumStake: BigInteger,
    val totalStake: BigInteger,
    val nominatorsCount: Int,
)
