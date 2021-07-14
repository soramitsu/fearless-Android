package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search.di

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
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search.SearchCustomValidatorsViewModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class SearchCustomValidatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(SearchCustomValidatorsViewModel::class)
    fun provideViewModel(
        addressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        router: StakingRouter,
        setupStakingSharedState: SetupStakingSharedState,
        searchCustomValidatorsInteractor: SearchCustomValidatorsInteractor,
        validatorRecommendatorFactory: ValidatorRecommendatorFactory,
        tokenUseCase: TokenUseCase
    ): ViewModel {
        return SearchCustomValidatorsViewModel(
            router,
            addressIconGenerator,
            searchCustomValidatorsInteractor,
            resourceManager,
            setupStakingSharedState,
            validatorRecommendatorFactory,
            tokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SearchCustomValidatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SearchCustomValidatorsViewModel::class.java)
    }
}
