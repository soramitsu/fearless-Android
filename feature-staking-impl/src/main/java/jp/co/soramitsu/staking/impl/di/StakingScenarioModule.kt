package jp.co.soramitsu.staking.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor

@InstallIn(SingletonComponent::class)
@Module
class StakingScenarioModule {

    @Provides
    fun provideScenarioInteractor(
        setupStakingSharedState: SetupStakingSharedState,
        stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor
    ): StakingScenarioInteractor {
        return when (val state = setupStakingSharedState.setupStakingProcess.value) {
            is SetupStakingProcess.Initial -> when (state.stakingType) {
                StakingType.RELAYCHAIN -> stakingRelayChainScenarioInteractor
                StakingType.PARACHAIN -> stakingParachainScenarioInteractor
                StakingType.POOL -> stakingRelayChainScenarioInteractor
            }

            is SetupStakingProcess.ReadyToSubmit.Stash,
            is SetupStakingProcess.SelectBlockProducersStep.Validators,
            is SetupStakingProcess.SetupStep.Stash -> {
                stakingRelayChainScenarioInteractor
            }

            is SetupStakingProcess.ReadyToSubmit.Parachain,
            is SetupStakingProcess.SelectBlockProducersStep.Collators,
            is SetupStakingProcess.SetupStep.Parachain -> {
                stakingParachainScenarioInteractor
            }
            is SetupStakingProcess.SetupStep.Pool -> stakingRelayChainScenarioInteractor
            is SetupStakingProcess.SelectBlockProducersStep.Pools -> stakingRelayChainScenarioInteractor
        }
    }
}
