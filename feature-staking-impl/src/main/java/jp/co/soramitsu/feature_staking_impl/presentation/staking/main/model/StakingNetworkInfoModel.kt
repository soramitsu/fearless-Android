package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model

sealed class StakingNetworkInfoModel(
    open val lockupPeriod: String,
    open val minimumStake: String,
    open val minimumStakeFiat: String?
) {
    data class RelayChain(
        override val lockupPeriod: String,
        override val minimumStake: String,
        override val minimumStakeFiat: String?,
        val totalStake: String,
        val totalStakeFiat: String?,
        val nominatorsCount: String
    ) : StakingNetworkInfoModel(
        lockupPeriod,
        minimumStake,
        minimumStakeFiat
    )
    data class Parachain(
        override val lockupPeriod: String,
        override val minimumStake: String,
        override val minimumStakeFiat: String?
    ) : StakingNetworkInfoModel(
        lockupPeriod,
        minimumStake,
        minimumStakeFiat
    )
}
