package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.select.di

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
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.select.SelectRewardDestinationViewModel

@Module(includes = [ViewModelModule::class])
class SelectRewardDestinationModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectRewardDestinationViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        rewardCalculatorFactory: RewardCalculatorFactory,
        resourceManager: ResourceManager,
        changeRewardDestinationInteractor: ChangeRewardDestinationInteractor,
        validationSystem: RewardDestinationValidationSystem,
        validationExecutor: ValidationExecutor,
        rewardDestinationMixin: RewardDestinationMixin.Presentation,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
    ): ViewModel {
        return SelectRewardDestinationViewModel(
            router,
            interactor,
            rewardCalculatorFactory,
            resourceManager,
            changeRewardDestinationInteractor,
            validationSystem,
            validationExecutor,
            feeLoaderMixin,
            rewardDestinationMixin
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): SelectRewardDestinationViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SelectRewardDestinationViewModel::class.java)
    }
}
