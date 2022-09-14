package jp.co.soramitsu.staking.impl.presentation.common

class StakingPoolSharedStateProvider {
    val mainState by lazy { StakingPoolSharedState<StakingPoolState>() }
    val setupState by lazy { StakingPoolSharedState<StakingPoolJoinFlowState>() }
    val manageState by lazy { StakingPoolSharedState<StakingPoolManageFlowState>() }
}
