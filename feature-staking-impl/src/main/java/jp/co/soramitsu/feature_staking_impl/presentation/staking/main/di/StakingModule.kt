package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_BOND_MORE
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_REDEEM
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewModel
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import javax.inject.Named

@Module(includes = [ViewModelModule::class])
class StakingModule {

    @Provides
    @ScreenScope
    fun provideStakingViewStateFactory(
        interactor: StakingInteractor,
        setupStakingSharedState: SetupStakingSharedState,
        resourceManager: ResourceManager,
        rewardCalculatorFactory: RewardCalculatorFactory,
        router: StakingRouter,
        welcomeStakingValidationSystem: WelcomeStakingValidationSystem,
        validationExecutor: ValidationExecutor
    ) = StakingViewStateFactory(
        interactor,
        setupStakingSharedState,
        resourceManager,
        router,
        rewardCalculatorFactory,
        welcomeStakingValidationSystem,
        validationExecutor
    )

    @Provides
    @IntoMap
    @ViewModelKey(StakingViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        alertsInteractor: AlertsInteractor,
        addressIconGenerator: AddressIconGenerator,
        stakingViewStateFactory: StakingViewStateFactory,
        router: StakingRouter,
        resourceManager: ResourceManager,
        @Named(SYSTEM_MANAGE_STAKING_REDEEM) redeemValidationSystem: ManageStakingValidationSystem,
        @Named(SYSTEM_MANAGE_STAKING_BOND_MORE) bondMoreValidationSystem: ManageStakingValidationSystem,
        validationExecutor: ValidationExecutor,
        stakingUpdateSystem: UpdateSystem,
        assetSelectorFactory: AssetSelectorMixin.Presentation.Factory
    ): ViewModel {
        return StakingViewModel(
            interactor,
            alertsInteractor,
            addressIconGenerator,
            stakingViewStateFactory,
            router,
            resourceManager,
            redeemValidationSystem,
            bondMoreValidationSystem,
            validationExecutor,
            stakingUpdateSystem,
            assetSelectorFactory,
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): StakingViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(StakingViewModel::class.java)
    }
}
