package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarningMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.impl.PhishingWarningProvider

@Module(includes = [ViewModelModule::class])
class ChooseAmountModule {

    @Provides
    fun providePhishingAddressMixin(interactor: WalletInteractor): PhishingWarningMixin {
        return PhishingWarningProvider(interactor)
    }

    @Provides
    @IntoMap
    @ViewModelKey(ChooseAmountViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        addressModelGenerator: AddressIconGenerator,
        externalAccountActions: ExternalAccountActions.Presentation,
        transferValidityChecks: TransferValidityChecks.Presentation,
        recipientAddress: String,
        phishingWarning: PhishingWarningMixin
    ): ViewModel {
        return ChooseAmountViewModel(
            interactor,
            router,
            addressModelGenerator,
            externalAccountActions,
            transferValidityChecks,
            recipientAddress,
            phishingWarning
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ChooseAmountViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ChooseAmountViewModel::class.java)
    }
}