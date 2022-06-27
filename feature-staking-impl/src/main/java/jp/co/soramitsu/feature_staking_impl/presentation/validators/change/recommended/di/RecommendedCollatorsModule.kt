package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.RecommendedCollatorsViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class RecommendedCollatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(RecommendedCollatorsViewModel::class)
    fun provideViewModel(
        collatorRecommendatorFactory: CollatorRecommendatorFactory,
        recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        setupStakingSharedState: SetupStakingSharedState,
        tokenUseCase: TokenUseCase
    ): ViewModel {
        return RecommendedCollatorsViewModel(
            router,
            collatorRecommendatorFactory,
            recommendationSettingsProviderFactory,
            addressIconGenerator,
            stakingInteractor,
            stakingParachainScenarioInteractor,
            resourceManager,
            setupStakingSharedState,
            tokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): RecommendedCollatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(RecommendedCollatorsViewModel::class.java)
    }
}
