package jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail.di

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
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail.PayoutDetailsViewModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable

@Module(includes = [ViewModelModule::class])
class PayoutDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(PayoutDetailsViewModel::class)
    fun provideViewModel(
        interactor: StakingInteractor,
        router: StakingRouter,
        payout: PendingPayoutParcelable,
        addressIconGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        resourceManager: ResourceManager
    ): ViewModel {
        return PayoutDetailsViewModel(
            interactor,
            router,
            payout,
            addressIconGenerator,
            externalAccountActions,
            resourceManager
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): PayoutDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(PayoutDetailsViewModel::class.java)
    }
}
