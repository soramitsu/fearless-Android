package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.di

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
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_DEFAULT
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_UNBOND
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.StakingBalanceViewModel
import javax.inject.Named

@Module(includes = [ViewModelModule::class])
class StakingBalanceModule {

    @Provides
    @IntoMap
    @ViewModelKey(StakingBalanceViewModel::class)
    fun provideViewModel(
        addressIconGenerator: AddressIconGenerator,
        stakingInteractor: StakingInteractor,
        @Named(SYSTEM_MANAGE_STAKING_DEFAULT) defaultActionValidationSystem: ManageStakingValidationSystem,
        @Named(SYSTEM_MANAGE_STAKING_UNBOND) unbondValidationSystem: ManageStakingValidationSystem,
        validationExecutor: ValidationExecutor,
        resourceManager: ResourceManager,
        router: StakingRouter,
    ): ViewModel {
        return StakingBalanceViewModel(
            router,
            addressIconGenerator,
            defaultActionValidationSystem,
            unbondValidationSystem,
            validationExecutor,
            resourceManager,
            stakingInteractor,
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
