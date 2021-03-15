package jp.co.soramitsu.feature_staking_impl.presentation.staking.di

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.staking.NominatorViewState

class StakingViewStateFactory(
    private val stakingInteractor: StakingInteractor,
) {

    fun createNominatorViewState(
        stakingState: StakingState.Stash.Nominator
    ) = NominatorViewState(
        nominatorState = stakingState,
        stakingInteractor = stakingInteractor
    )
}
