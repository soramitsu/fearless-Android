package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.StakingBalanceViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@Module(includes = [ViewModelModule::class])
class StakingBalanceModule {

    @Provides
    @IntoMap
    @ViewModelKey(StakingBalanceViewModel::class)
    fun provideViewModel(
        stakingInteractor: StakingInteractor,
        stakingScenarioInteractor: StakingScenarioInteractor,
        unbondingInteractor: UnbondInteractor,
        validationExecutor: ValidationExecutor,
        resourceManager: ResourceManager,
        router: StakingRouter,
        collatorAddress: String?
    ): ViewModel {
        return StakingBalanceViewModel(
            router,
            validationExecutor,
            unbondingInteractor,
            resourceManager,
            stakingInteractor,
            stakingScenarioInteractor,
            collatorAddress
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StakingBalanceViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StakingBalanceViewModel::class.java)
    }
}
