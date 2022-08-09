package jp.co.soramitsu.featurestakingimpl.presentation.staking.main.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.featurestakingimpl.domain.StakingInteractor
import jp.co.soramitsu.featurestakingimpl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.featurestakingimpl.presentation.StakingRouter
import jp.co.soramitsu.featurestakingimpl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.featurestakingimpl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioInteractor

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
        parachainScenarioInteractor: StakingParachainScenarioInteractor
    ) = StakingViewStateFactory(
        interactor,
        setupStakingSharedState,
        resourceManager,
        router,
        rewardCalculatorFactory,
        validationExecutor,
        relayChainScenarioInteractor,
        parachainScenarioInteractor
    )
}
