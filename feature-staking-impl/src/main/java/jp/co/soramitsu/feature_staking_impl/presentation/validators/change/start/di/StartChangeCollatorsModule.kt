package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start.StartChangeCollatorsViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor

@Module(includes = [ViewModelModule::class])
class StartChangeCollatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(StartChangeCollatorsViewModel::class)
    fun provideViewModel(
        collatorRecommendatorFactory: CollatorRecommendatorFactory,
        router: StakingRouter,
        sharedState: SetupStakingSharedState,
        resourceManager: ResourceManager,
        stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
        stakingInteractor: StakingInteractor
    ): ViewModel {
        return StartChangeCollatorsViewModel(
            router,
            collatorRecommendatorFactory,
            sharedState,
            resourceManager,
            stakingParachainScenarioInteractor,
            stakingInteractor
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StartChangeCollatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StartChangeCollatorsViewModel::class.java)
    }
}
