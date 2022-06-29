package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ConfirmRebondModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmRebondViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        router: StakingRouter,
        rebondInteractor: RebondInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        validationSystem: RebondValidationSystem,
        iconGenerator: AddressIconGenerator,
        chainRegistry: ChainRegistry,
        externalAccountActions: ExternalAccountActions.Presentation,
        payload: ConfirmRebondPayload,
    ): ViewModel {
        return ConfirmRebondViewModel(
            router,
            interactor,
            stakingRelayChainScenarioInteractor,
            rebondInteractor,
            resourceManager,
            validationExecutor,
            validationSystem,
            iconGenerator,
            chainRegistry,
            externalAccountActions,
            feeLoaderMixin,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): ConfirmRebondViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmRebondViewModel::class.java)
    }
}
