package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.review.di

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
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.review.ReviewCustomValidatorsViewModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase

@Module(includes = [ViewModelModule::class])
class ReviewCustomValidatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ReviewCustomValidatorsViewModel::class)
    fun provideViewModel(
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        setupStakingSharedState: SetupStakingSharedState,
        tokenUseCase: TokenUseCase
    ): ViewModel {
        return ReviewCustomValidatorsViewModel(
            router,
            addressIconGenerator,
            stakingInteractor,
            resourceManager,
            setupStakingSharedState,
            tokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ReviewCustomValidatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ReviewCustomValidatorsViewModel::class.java)
    }
}
