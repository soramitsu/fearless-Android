package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.ConfirmBondMoreViewModel

@Module(includes = [ViewModelModule::class])
class ConfirmBondMoreModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmBondMoreViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        bondMoreInteractor: BondMoreInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        validationSystem: BondMoreValidationSystem,
        iconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        payload: ConfirmBondMorePayload,
    ): ViewModel {
        return ConfirmBondMoreViewModel(
            router,
            interactor,
            bondMoreInteractor,
            resourceManager,
            validationExecutor,
            iconGenerator,
            validationSystem,
            externalAccountActions,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmBondMoreViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmBondMoreViewModel::class.java)
    }
}
