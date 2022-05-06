package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingInfoViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState1
import kotlinx.coroutines.flow.Flow

interface StakingScenarioViewModel {
//    fun getLoadingStakingState(): Flow<LoadingState<StakingState>>
//    fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>>

    suspend fun stakingInfoViewStateFlow(): Flow<StakingInfoViewState>
    suspend fun stakingViewState(): Flow<StakingViewState1>

}
