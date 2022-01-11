package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set.di

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
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.set.SetControllerViewModel

@Module(includes = [ViewModelModule::class])
class SetControllerModule {
    @Provides
    @IntoMap
    @ViewModelKey(SetControllerViewModel::class)
    fun provideViewModel(
        interactor: ControllerInteractor,
        stackingInteractor: StakingInteractor,
        addressIconGenerator: AddressIconGenerator,
        router: StakingRouter,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        externalActions: ExternalAccountActions.Presentation,
        appLinksProvider: AppLinksProvider,
        resourceManager: ResourceManager,
        addressDisplayUseCase: AddressDisplayUseCase,
        validationExecutor: ValidationExecutor,
        validationSystem: SetControllerValidationSystem
    ): ViewModel {
        return SetControllerViewModel(
            interactor,
            stackingInteractor,
            addressIconGenerator,
            router,
            feeLoaderMixin,
            externalActions,
            appLinksProvider,
            resourceManager,
            addressDisplayUseCase,
            validationExecutor,
            validationSystem
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SetControllerViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SetControllerViewModel::class.java)
    }
}
