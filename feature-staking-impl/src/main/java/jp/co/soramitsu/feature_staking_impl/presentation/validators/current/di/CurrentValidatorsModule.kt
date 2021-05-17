package jp.co.soramitsu.feature_staking_impl.presentation.validators.current.di

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
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.CurrentValidatorsViewModel

@Module(includes = [ViewModelModule::class])
class CurrentValidatorsModule {

    @Provides
    @IntoMap
    @ViewModelKey(CurrentValidatorsViewModel::class)
    fun provideViewModel(
        stakingInteractor: StakingInteractor,
        resourceManager: ResourceManager,
        iconGenerator: AddressIconGenerator,
        currentValidatorsInteractor: CurrentValidatorsInteractor,
        setupStakingSharedState: SetupStakingSharedState,
        router: StakingRouter,
    ): ViewModel {
        return CurrentValidatorsViewModel(
            router,
            resourceManager,
            stakingInteractor,
            iconGenerator,
            currentValidatorsInteractor,
            setupStakingSharedState
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): CurrentValidatorsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CurrentValidatorsViewModel::class.java)
    }
}
