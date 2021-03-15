package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.liveData
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import kotlinx.coroutines.flow.collect

sealed class StakingViewState

class NominatorViewState(
    private val nominatorState: StakingState.Stash.Nominator,
    private val stakingInteractor: StakingInteractor,
) : StakingViewState() {

    val nominatorSummaryLiveData = liveData {
        stakingInteractor.observeNominatorSummary(nominatorState).collect { emit(it) }
    }
}

object StakingViewStateStub : StakingViewState()
