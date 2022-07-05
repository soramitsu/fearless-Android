package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMoreViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin

@Module(includes = [ViewModelModule::class])
class SelectBondMoreModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectBondMoreViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingScenarioInteractor: StakingScenarioInteractor,
        router: StakingRouter,
        bondMoreInteractor: BondMoreInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        validationSystem: BondMoreValidationSystem,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        payload: SelectBondMorePayload
    ): ViewModel {
        return SelectBondMoreViewModel(
            router,
            interactor,
            stakingScenarioInteractor,
            bondMoreInteractor,
            resourceManager,
            validationExecutor,
            validationSystem,
            feeLoaderMixin,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SelectBondMoreViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SelectBondMoreViewModel::class.java)
    }
}
