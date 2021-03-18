package jp.co.soramitsu.feature_staking_impl.presentation.setup.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.domain.setup.validations.StakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.setup.SetupStakingViewModel

@Module(includes = [ViewModelModule::class])
class SetupStakingModule {

    @Provides
    @IntoMap
    @ViewModelKey(SetupStakingViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        addressIconGenerator: AddressIconGenerator,
        rewardCalculatorFactory: RewardCalculatorFactory,
        resourceManager: ResourceManager,
        maxFeeEstimator: MaxFeeEstimator,
        validationSystem: ValidationSystem<SetupStakingPayload, StakingValidationFailure>,
        appLinksProvider: AppLinksProvider,
        setupStakingSharedState: SetupStakingSharedState,
        feeLoaderMixin: FeeLoaderMixin.Presentation
    ): ViewModel {
        return SetupStakingViewModel(
            router,
            interactor,
            addressIconGenerator,
            rewardCalculatorFactory,
            resourceManager,
            maxFeeEstimator,
            validationSystem,
            appLinksProvider,
            setupStakingSharedState,
            feeLoaderMixin
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SetupStakingViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SetupStakingViewModel::class.java)
    }
}
