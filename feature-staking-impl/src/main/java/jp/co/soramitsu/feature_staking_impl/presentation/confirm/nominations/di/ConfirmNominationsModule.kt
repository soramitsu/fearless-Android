package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations.di

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
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations.ConfirmNominationsViewModel

@Module(includes = [ViewModelModule::class])
class ConfirmNominationsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmNominationsViewModel::class)
    fun provideViewModel(
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        stakingSharedState: StakingSharedState
    ): ViewModel {
        return ConfirmNominationsViewModel(
            router,
            addressIconGenerator,
            stakingInteractor,
            resourceManager,
            stakingSharedState
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmNominationsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmNominationsViewModel::class.java)
    }
}