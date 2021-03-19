package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

data class StakingNetworkInfoModel(
    val lockupPeriod: String,
    val minimumStake: String,
    val minimumStakeFiat: String?,
    val totalStake: String,
    val totalStakeFiat: String?,
    val nominatorsCount: String
)
