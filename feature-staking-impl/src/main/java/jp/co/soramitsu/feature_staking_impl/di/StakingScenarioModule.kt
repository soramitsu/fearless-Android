package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@Module(
    includes = [
    ]
)
class StakingScenarioModule {

    @Provides
    fun provideScenarioInteractor(
        setupStakingSharedState: SetupStakingSharedState,
        stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor
    ): StakingScenarioInteractor {
        return when (val state = setupStakingSharedState.setupStakingProcess.value) {
            is SetupStakingProcess.Initial -> if (state.stakingType == Chain.Asset.StakingType.RELAYCHAIN)
                stakingRelayChainScenarioInteractor
            else stakingParachainScenarioInteractor

            is SetupStakingProcess.ReadyToSubmit.Stash,
            is SetupStakingProcess.SelectBlockProducersStep.Validators,
            is SetupStakingProcess.SetupStep.Stash -> stakingRelayChainScenarioInteractor

            is SetupStakingProcess.ReadyToSubmit.Parachain,
            is SetupStakingProcess.SelectBlockProducersStep.Collators,
            is SetupStakingProcess.SetupStep.Parachain -> stakingParachainScenarioInteractor
        }
    }
}
