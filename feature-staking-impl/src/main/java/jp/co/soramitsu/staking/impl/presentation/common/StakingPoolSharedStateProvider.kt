package jp.co.soramitsu.staking.impl.presentation.common

class StakingPoolSharedStateProvider {
    val mainState by lazy { StakingPoolSharedState<StakingPoolState>() }
    val joinFlowState by lazy { StakingPoolSharedState<StakingPoolJoinFlowState>() }
    val createFlowState by lazy { StakingPoolSharedState<StakingPoolCreateFlowState>() }
    val manageState by lazy { StakingPoolSharedState<StakingPoolManageFlowState>() }
    val selectValidatorsState by lazy { StakingPoolSharedState<SelectValidatorFlowState>() }

    val requireMainState: StakingPoolState
        get() = requireNotNull(mainState.get())
    val requireCreateState: StakingPoolCreateFlowState
        get() = requireNotNull(createFlowState.get())
    val requireJoinState: StakingPoolJoinFlowState
        get() = requireNotNull(joinFlowState.get())
    val requireSelectValidatorsState: SelectValidatorFlowState
        get() = requireNotNull(selectValidatorsState.get())
}
