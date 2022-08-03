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
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.RecommendedValidatorsViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class RecommendedValidatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(RecommendedValidatorsViewModel::class)
    fun provideViewModel(
        validatorRecommendatorFactory: ValidatorRecommendatorFactory,
        recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        setupStakingSharedState: SetupStakingSharedState,
        tokenUseCase: TokenUseCase
    ): ViewModel {
        return RecommendedValidatorsViewModel(
            router,
            validatorRecommendatorFactory,
            recommendationSettingsProviderFactory,
            addressIconGenerator,
            stakingInteractor,
            stakingRelayChainScenarioInteractor,
            resourceManager,
            setupStakingSharedState,
            tokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): RecommendedValidatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(RecommendedValidatorsViewModel::class.java)
    }
}
