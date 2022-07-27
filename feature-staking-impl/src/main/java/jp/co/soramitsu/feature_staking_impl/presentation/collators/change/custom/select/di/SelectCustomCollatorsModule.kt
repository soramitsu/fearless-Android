package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select.di

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
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select.SelectCustomCollatorsViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class SelectCustomCollatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectCustomCollatorsViewModel::class)
    fun provideViewModel(
        collatorRecommendatorFactory: CollatorRecommendatorFactory,
        recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
        addressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        setupStakingSharedState: SetupStakingSharedState,
        router: StakingRouter,
        tokenUseCase: TokenUseCase,
        settingsStorage: SettingsStorage
    ): ViewModel {
        return SelectCustomCollatorsViewModel(
            router,
            collatorRecommendatorFactory,
            recommendationSettingsProviderFactory,
            addressIconGenerator,
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
    ): SelectCustomCollatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory)[SelectCustomCollatorsViewModel::class.java]
    }
}
