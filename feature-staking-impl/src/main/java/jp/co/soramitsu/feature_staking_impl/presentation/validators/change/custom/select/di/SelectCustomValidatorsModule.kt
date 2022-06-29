package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select.di

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
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select.SelectCustomValidatorsViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class SelectCustomValidatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectCustomValidatorsViewModel::class)
    fun provideViewModel(
        validatorRecommendatorFactory: ValidatorRecommendatorFactory,
        recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        resourceManager: ResourceManager,
        setupStakingSharedState: SetupStakingSharedState,
        router: StakingRouter,
        tokenUseCase: TokenUseCase,
        settingsStorage: SettingsStorage
    ): ViewModel {
        return SelectCustomValidatorsViewModel(
            router,
            validatorRecommendatorFactory,
            recommendationSettingsProviderFactory,
            addressIconGenerator,
            stakingInteractor,
            stakingRelayChainScenarioInteractor,
            resourceManager,
            setupStakingSharedState,
            tokenUseCase,
            settingsStorage
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SelectCustomValidatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SelectCustomValidatorsViewModel::class.java)
    }
}
