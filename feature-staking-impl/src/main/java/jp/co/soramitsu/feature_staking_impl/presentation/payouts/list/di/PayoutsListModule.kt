package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.PayoutsListViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor

@Module(includes = [ViewModelModule::class])
class PayoutsListModule {

    @Provides
    @IntoMap
    @ViewModelKey(PayoutsListViewModel::class)
    fun provideViewModel(
        stakingInteractor: StakingInteractor,
        relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter,
    ): ViewModel {
        return PayoutsListViewModel(
            router,
            resourceManager,
            stakingInteractor,
            relayChainScenarioInteractor
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): PayoutsListViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(PayoutsListViewModel::class.java)
    }
}
