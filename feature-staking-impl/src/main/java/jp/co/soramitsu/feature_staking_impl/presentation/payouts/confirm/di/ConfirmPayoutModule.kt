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
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.ConfirmPayoutViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable

@Module(includes = [ViewModelModule::class])
class ConfirmPayoutModule {

    @Provides
    @IntoMap
    @ViewModelKey(ConfirmPayoutViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        payouts: List<PendingPayoutParcelable>,
        addressIconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        feeLoaderMixin: FeeLoaderMixin.Presentation,
        validationSystem: ValidationSystem<MakePayoutPayload, PayoutValidationFailure>,
        resourceManager: ResourceManager
    ): ViewModel {
        return ConfirmPayoutViewModel(
            interactor,
            router,
            payouts,
            addressIconGenerator,
            externalAccountActions,
            feeLoaderMixin,
            validationSystem,
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
