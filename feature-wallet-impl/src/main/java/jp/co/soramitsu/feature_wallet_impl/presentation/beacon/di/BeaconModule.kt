package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.BeaconApi
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.BeaconViewModel

@Module(includes = [ViewModelModule::class])
class BeaconModule {

    @Provides
    @ScreenScope
    fun provideBeaconApi(
        gson: Gson,
        accountRepository: AccountRepository,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = BeaconApi(gson, accountRepository, runtimeProperty)

    @Provides
    @IntoMap
    @ViewModelKey(BeaconViewModel::class)
    fun provideViewModel(
        beaconApi: BeaconApi,
        router: WalletRouter,
        interactor: WalletInteractor,
        iconAddressIconGenerator: AddressIconGenerator,
        qrContent: String
    ): ViewModel {
        return BeaconViewModel(
            beaconApi,
            router,
            interactor,
            iconAddressIconGenerator,
            qrContent
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): BeaconViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BeaconViewModel::class.java)
    }
}
