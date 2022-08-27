package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel

sealed class StakingAssetInfoViewState {
    abstract val title: String

    data class StakingPool(
        override val title: String,
        val guide: String,
        val minToJoin: TitleValueViewState,
        val minToCreate: TitleValueViewState,
        val existingPools: TitleValueViewState,
        val possiblePools: TitleValueViewState,
        val maxMembersInPool: TitleValueViewState,
        val maxPoolsMembers: TitleValueViewState
    ) : StakingAssetInfoViewState() {
        companion object
    }

    data class RelayChain(
        override val title: String,
        val stories: String, // todo stories view state
        val totalStaked: TitleValueViewState,
        val minStake: TitleValueViewState,
        val activeNominators: TitleValueViewState,
        val unstakingPeriod: TitleValueViewState,
    ) : StakingAssetInfoViewState() {
        companion object
    }

    data class Parachain(
        override val title: String,
        val stories: String, // todo stories view state
        val minStake: TitleValueViewState,
        val unstakingPeriod: TitleValueViewState,
    ) : StakingAssetInfoViewState() {
        companion object
    }
}

fun StakingAssetInfoViewState.StakingPool.Companion.default(resourceManager: ResourceManager) = StakingAssetInfoViewState.StakingPool(
    title = resourceManager.getString(R.string.pool_staking_title),
    guide = resourceManager.getString(R.string.pool_staking_main_description_title),
    minToJoin = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_min_join_title), null),
    minToCreate = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_min_create_title), null),
    existingPools = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_existing_pools_title), null),
    possiblePools = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_possible_pools_title), null),
    maxMembersInPool = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_max_members_inpool_title), null),
    maxPoolsMembers = TitleValueViewState(resourceManager.getString(R.string.pool_staking_main_max_pool_members_title), null)
)

fun StakingAssetInfoViewState.RelayChain.Companion.default(resourceManager: ResourceManager) = StakingAssetInfoViewState.RelayChain(
    title = resourceManager.getString(R.string.pool_staking_title),
    stories = "",
    totalStaked = TitleValueViewState(resourceManager.getString(R.string.staking_total_staked), null),
    minStake = TitleValueViewState(resourceManager.getString(R.string.staking_main_minimum_stake_title), null),
    activeNominators = TitleValueViewState(resourceManager.getString(R.string.staking_main_active_nominators_title), null),
    unstakingPeriod = TitleValueViewState(resourceManager.getString(R.string.staking_main_lockup_period_title), null)
)

fun StakingAssetInfoViewState.Parachain.Companion.default(resourceManager: ResourceManager) = StakingAssetInfoViewState.Parachain(
    title = resourceManager.getString(R.string.pool_staking_title),
    stories = "",
    minStake = TitleValueViewState(resourceManager.getString(R.string.staking_main_minimum_stake_title), null),
    unstakingPeriod = TitleValueViewState(resourceManager.getString(R.string.staking_main_lockup_period_title), null)
)

fun StakingAssetInfoViewState.StakingPool.update(state: StakingNetworkInfoModel.Pool): StakingAssetInfoViewState.StakingPool {
    return copy(
        minToCreate = minToCreate.copy(value = state.minToCreate, additionalValue = state.minToCreateFiat),
        minToJoin = minToJoin.copy(value = state.minToJoin, additionalValue = state.minToJoinFiat),
        existingPools = existingPools.copy(value = state.existingPools),
        possiblePools = possiblePools.copy(value = state.possiblePools),
        maxMembersInPool = maxMembersInPool.copy(value = state.maxMembersInPool),
        maxPoolsMembers = maxPoolsMembers.copy(value = state.maxPoolsMembers)
    )
}

fun StakingAssetInfoViewState.RelayChain.update(state: StakingNetworkInfoModel.RelayChain): StakingAssetInfoViewState.RelayChain {
    return copy(
        minStake = minStake.copy(value = state.minimumStake, additionalValue = state.minimumStakeFiat),
        unstakingPeriod = unstakingPeriod.copy(value = state.lockupPeriod),
        totalStaked = totalStaked.copy(value = state.totalStake, additionalValue = state.totalStakeFiat),
        activeNominators = activeNominators.copy(value = state.nominatorsCount)
    )
}

fun StakingAssetInfoViewState.Parachain.update(state: StakingNetworkInfoModel.Parachain): StakingAssetInfoViewState.Parachain {
    return copy(
        minStake = minStake.copy(value = state.minimumStake, additionalValue = state.minimumStakeFiat),
        unstakingPeriod = unstakingPeriod.copy(value = state.lockupPeriod)
    )
}
