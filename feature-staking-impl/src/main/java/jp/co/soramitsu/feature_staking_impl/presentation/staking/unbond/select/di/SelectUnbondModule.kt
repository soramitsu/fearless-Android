package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select.SelectUnbondViewModel

@Module(includes = [ViewModelModule::class])
class SelectUnbondModule {

    @Provides
    @IntoMap
    @ViewModelKey(SelectUnbondViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        unbondInteractor: UnbondInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        validationSystem: UnbondValidationSystem,
        feeLoaderMixin: FeeLoaderMixin.Presentation
    ): ViewModel {
        return SelectUnbondViewModel(
            router,
            interactor,
            unbondInteractor,
            resourceManager,
            validationExecutor,
            validationSystem,
            feeLoaderMixin
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SelectUnbondViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SelectUnbondViewModel::class.java)
    }
}
