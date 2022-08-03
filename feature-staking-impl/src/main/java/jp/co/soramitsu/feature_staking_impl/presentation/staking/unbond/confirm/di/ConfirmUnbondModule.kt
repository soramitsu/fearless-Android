package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.di

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
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ConfirmUnbondModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmUnbondViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        router: StakingRouter,
        unbondInteractor: UnbondInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        validationSystem: UnbondValidationSystem,
        iconGenerator: AddressIconGenerator,
        chainRegistry: ChainRegistry,
        externalAccountActions: ExternalAccountActions.Presentation,
        payload: ConfirmUnbondPayload,
    ): ViewModel {
        return ConfirmUnbondViewModel(
            router,
            interactor,
            stakingRelayChainScenarioInteractor,
            unbondInteractor,
            resourceManager,
            validationExecutor,
            iconGenerator,
            chainRegistry,
            validationSystem,
            externalAccountActions,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): ConfirmUnbondViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmUnbondViewModel::class.java)
    }
}
