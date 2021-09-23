package jp.co.soramitsu.feature_staking_impl.presentation.confirm.di

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
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.ConfirmStakingViewModel
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin

@Module(includes = [ViewModelModule::class])
class ConfirmStakingModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmStakingViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        addressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        addressDisplayUseCase: AddressDisplayUseCase,
        setupStakingInteractor: SetupStakingInteractor,
        validationSystem: ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure>,
        validationExecutor: ValidationExecutor,
        setupStakingSharedState: SetupStakingSharedState,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        externalAccountActions: ExternalAccountActions.Presentation,
    ): ViewModel {
        return ConfirmStakingViewModel(
            router,
            interactor,
            addressIconGenerator,
            addressDisplayUseCase,
            resourceManager,
            validationSystem,
            setupStakingSharedState,
            setupStakingInteractor,
            feeLoaderMixin,
            externalAccountActions,
            validationExecutor
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmStakingViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmStakingViewModel::class.java)
    }
}
