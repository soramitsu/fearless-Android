package jp.co.soramitsu.staking.impl.presentation.staking.main.model

sealed class StakingNetworkInfoModel {
    data class RelayChain(
        val lockupPeriod: String,
        val minimumStake: String,
        val minimumStakeFiat: String?,
        val totalStake: String,
        val totalStakeFiat: String?,
        val nominatorsCount: String
    ) : StakingNetworkInfoModel()

    data class Parachain(
        val lockupPeriod: String,
        val minimumStake: String,
        val minimumStakeFiat: String?
    ) : StakingNetworkInfoModel()

    data class Pool(
        val minToJoin: String,
        val minToJoinFiat: String?,
        val minToCreate: String,
        val minToCreateFiat: String?,
        val existingPools: String,
        val possiblePools: String?,
        val maxMembersInPool: String,
        val maxPoolsMembers: String?
    ) : StakingNetworkInfoModel()
}
