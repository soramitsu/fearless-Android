package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarningMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.impl.PhishingWarningProvider
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

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
        walletConstants: WalletConstants,
        assetPayload: AssetPayload,
        phishingWarning: PhishingWarningMixin,
        chainRegistry: ChainRegistry
    ): ViewModel {
        return ChooseAmountViewModel(
            interactor,
            router,
            addressModelGenerator,
            externalAccountActions,
            transferValidityChecks,
            walletConstants,
            recipientAddress,
            assetPayload,
            phishingWarning,
            chainRegistry
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
