package jp.co.soramitsu.staking.impl.presentation.staking.main.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor

@InstallIn(SingletonComponent::class)
@Module(includes = [ViewModelModule::class])
class StakingModule {

    @Provides
    fun provideStakingViewStateFactory(
        interactor: StakingInteractor,
        setupStakingSharedState: SetupStakingSharedState,
        resourceManager: ResourceManager,
        rewardCalculatorFactory: RewardCalculatorFactory,
        router: StakingRouter,
        validationExecutor: ValidationExecutor,
        relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        parachainScenarioInteractor: StakingParachainScenarioInteractor,
        stakingRewardsScenario: SoraStakingRewardsScenario
    ) = StakingViewStateFactory(
        interactor,
        setupStakingSharedState,
        resourceManager,
        router,
        rewardCalculatorFactory,
        validationExecutor,
        relayChainScenarioInteractor,
        parachainScenarioInteractor,
        stakingRewardsScenario
    )
}
