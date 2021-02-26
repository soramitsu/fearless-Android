package jp.co.soramitsu.feature_staking_impl.presentation.staking.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.StakingViewModel

@Module(includes = [ViewModelModule::class])
class StakingModule {

    @Provides
    @IntoMap
    @ViewModelKey(StakingViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        addressIconGenerator: AddressIconGenerator,
        rewardCalculatorFactory: RewardCalculatorFactory,
        resourceManager: ResourceManager
    ): ViewModel {
        return StakingViewModel(router, interactor, addressIconGenerator, rewardCalculatorFactory, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StakingViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StakingViewModel::class.java)
    }
}