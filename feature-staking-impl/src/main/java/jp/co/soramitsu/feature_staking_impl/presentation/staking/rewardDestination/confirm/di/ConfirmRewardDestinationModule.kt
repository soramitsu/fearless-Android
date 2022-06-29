package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.di

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
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.ConfirmRewardDestinationViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ConfirmRewardDestinationModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmRewardDestinationViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        router: StakingRouter,
        addressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        validationSystem: RewardDestinationValidationSystem,
        validationExecutor: ValidationExecutor,
        rewardDestinationInteractor: ChangeRewardDestinationInteractor,
        chainRegistry: ChainRegistry,
        externalAccountActions: ExternalAccountActions.Presentation,
        addressDisplayUseCase: AddressDisplayUseCase,
        payload: ConfirmRewardDestinationPayload
    ): ViewModel {
        return ConfirmRewardDestinationViewModel(
            router,
            interactor,
            stakingRelayChainScenarioInteractor,
            addressIconGenerator,
            resourceManager,
            validationSystem,
            rewardDestinationInteractor,
            chainRegistry,
            externalAccountActions,
            addressDisplayUseCase,
            validationExecutor,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmRewardDestinationViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmRewardDestinationViewModel::class.java)
    }
}
