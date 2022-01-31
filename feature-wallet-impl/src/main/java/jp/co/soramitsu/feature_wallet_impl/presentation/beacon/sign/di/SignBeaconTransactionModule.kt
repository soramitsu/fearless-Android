package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.di

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
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign.SignBeaconTransactionViewModel

@Module(includes = [ViewModelModule::class])
class SignBeaconTransactionModule {

    @Provides
    @IntoMap
    @ViewModelKey(SignBeaconTransactionViewModel::class)
    fun provideViewModel(
        beaconInteractor: BeaconInteractor,
        router: WalletRouter,
        interactor: WalletInteractor,
        iconAddressIconGenerator: AddressIconGenerator,
        payloadToSign: String,
        resourceManager: ResourceManager,
        feeLoaderProvider: FeeLoaderMixin.Presentation
    ): ViewModel {
        return SignBeaconTransactionViewModel(
            beaconInteractor,
            router,
            interactor,
            iconAddressIconGenerator,
            payloadToSign,
            resourceManager,
            feeLoaderProvider
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SignBeaconTransactionViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SignBeaconTransactionViewModel::class.java)
    }
}
