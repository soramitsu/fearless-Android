package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import kotlinx.coroutines.flow.MutableStateFlow

class StakingPoolSharedStateProvider {
    val mainState by lazy { StakingPoolSharedState<StakingPoolState>() }
    val joinFlowState by lazy { StakingPoolSharedState<StakingPoolJoinFlowState>() }
    val createFlowState by lazy { StakingPoolSharedState<StakingPoolCreateFlowState>() }
    val manageState by lazy { StakingPoolSharedState<StakingPoolManageFlowState>() }
    val selectValidatorsState by lazy { StakingPoolSharedState<SelectValidatorFlowState>() }
    val selectedValidatorsState by lazy { StakingPoolSharedState<SelectedValidatorsFlowState>() }
    val editPoolState by lazy { StakingPoolSharedState<EditPoolFlowState>() }
    val poolsCache: MutableStateFlow<Map<Int, PoolInfo>> = MutableStateFlow(emptyMap())

    val requireMainState: StakingPoolState
        get() = requireNotNull(mainState.get())
    val requireCreateState: StakingPoolCreateFlowState
        get() = requireNotNull(createFlowState.get())
    val requireJoinState: StakingPoolJoinFlowState
        get() = requireNotNull(joinFlowState.get())
    val requireSelectValidatorsState: SelectValidatorFlowState
        get() = requireNotNull(selectValidatorsState.get())
    val requireSelectedValidatorsState: SelectedValidatorsFlowState
        get() = requireNotNull(selectedValidatorsState.get())
    val requireEditPoolState: EditPoolFlowState
        get() = requireNotNull(editPoolState.get())
    val requireManageState: StakingPoolManageFlowState
        get() = requireNotNull(manageState.get())
}
