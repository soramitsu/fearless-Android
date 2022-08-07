package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.di

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
import jp.co.soramitsu.feature_staking_impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemViewModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class RedeemModule {

    @Provides
    @IntoMap
    @ViewModelKey(RedeemViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        stakingScenarioInteractor: StakingScenarioInteractor,
        router: StakingRouter,
        redeemInteractor: RedeemInteractor,
        resourceManager: ResourceManager,
        validationExecutor: ValidationExecutor,
        iconGenerator: AddressIconGenerator,
        chainRegistry: ChainRegistry,
        externalAccountActions: ExternalAccountActions.Presentation,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        payload: RedeemPayload
    ): ViewModel {
        return RedeemViewModel(
            router,
            stakingScenarioInteractor,
            interactor,
            redeemInteractor,
            resourceManager,
            validationExecutor,
            iconGenerator,
            chainRegistry,
            feeLoaderMixin,
            externalAccountActions,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): RedeemViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(RedeemViewModel::class.java)
    }
}
