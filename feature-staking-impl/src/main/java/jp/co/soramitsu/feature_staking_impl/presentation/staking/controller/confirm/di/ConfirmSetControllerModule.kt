package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.di

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
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerViewModel

@Module(includes = [ViewModelModule::class])
class ConfirmSetControllerModule {
    @Provides
    @IntoMap
    @ViewModelKey(ConfirmSetControllerViewModel::class)
    fun provideViewModule(
        router: StakingRouter,
        controllerInteractor: ControllerInteractor,
        addressIconGenerator: AddressIconGenerator,
        payload: ConfirmSetControllerPayload,
        interactor: StakingInteractor,
        resourceManager: ResourceManager,
        externalActions: ExternalAccountActions.Presentation,
        validationExecutor: ValidationExecutor,
        validationSystem: SetControllerValidationSystem
    ): ViewModel {
        return ConfirmSetControllerViewModel(
            router,
            controllerInteractor,
            addressIconGenerator,
            payload,
            interactor,
            resourceManager,
            externalActions,
            validationExecutor,
            validationSystem
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmSetControllerViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmSetControllerViewModel::class.java)
    }
}
