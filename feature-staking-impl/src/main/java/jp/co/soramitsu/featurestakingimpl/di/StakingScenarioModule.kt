package jp.co.soramitsu.featurestakingimpl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

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
            is SetupStakingProcess.Initial -> if (state.stakingType == Chain.Asset.StakingType.RELAYCHAIN) {
                stakingRelayChainScenarioInteractor
            } else {
                stakingParachainScenarioInteractor
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
        }
    }
}
