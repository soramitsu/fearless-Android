package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings.CustomValidatorsSettingsViewModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class CustomValidatorsSettingsModule {

    @Provides
    @IntoMap
    @ViewModelKey(CustomValidatorsSettingsViewModel::class)
    fun provideViewModel(
        recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
        router: StakingRouter,
        tokenUseCase: TokenUseCase
    ): ViewModel {
        return CustomValidatorsSettingsViewModel(
            router,
            recommendationSettingsProviderFactory,
            tokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CustomValidatorsSettingsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CustomValidatorsSettingsViewModel::class.java)
    }
}
