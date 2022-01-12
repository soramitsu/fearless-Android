package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom.CustomRebondViewModel

@Module(includes = [ViewModelModule::class])
class CustomRebondModule {

    @Provides
    @IntoMap
    @ViewModelKey(CustomRebondViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        rebondInteractor: RebondInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        validationSystem: RebondValidationSystem,
    ): ViewModel {
        return CustomRebondViewModel(
            router,
            interactor,
            rebondInteractor,
            resourceManager,
            validationExecutor,
            validationSystem,
            feeLoaderMixin
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): CustomRebondViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CustomRebondViewModel::class.java)
    }
}
