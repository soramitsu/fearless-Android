package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.di

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
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.ConfirmPayoutViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class ConfirmPayoutModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmPayoutViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        router: StakingRouter,
        payload: ConfirmPayoutPayload,
        payoutInteractor: PayoutInteractor,
        addressIconGenerator: AddressIconGenerator,
        chainRegistry: ChainRegistry,
        externalAccountActions: ExternalAccountActions.Presentation,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        validationSystem: ValidationSystem<MakePayoutPayload, PayoutValidationFailure>,
        validationExecutor: ValidationExecutor,
        addressDisplayUseCase: AddressDisplayUseCase,
        resourceManager: ResourceManager
    ): ViewModel {
        return ConfirmPayoutViewModel(
            interactor,
            relayChainScenarioInteractor,
            payoutInteractor,
            router,
            payload,
            addressIconGenerator,
            chainRegistry,
            externalAccountActions,
            feeLoaderMixin,
            addressDisplayUseCase,
            validationSystem,
            validationExecutor,
            resourceManager
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ConfirmPayoutViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ConfirmPayoutViewModel::class.java)
    }
}
