package jp.co.soramitsu.staking.impl.presentation.common

class StakingPoolSharedStateProvider {
    val mainState by lazy { StakingPoolSharedState<StakingPoolState>() }
    val joinFlowState by lazy { StakingPoolSharedState<StakingPoolJoinFlowState>() }
    val createFlowState by lazy { StakingPoolSharedState<StakingPoolCreateFlowState>() }
    val manageState by lazy { StakingPoolSharedState<StakingPoolManageFlowState>() }
}
