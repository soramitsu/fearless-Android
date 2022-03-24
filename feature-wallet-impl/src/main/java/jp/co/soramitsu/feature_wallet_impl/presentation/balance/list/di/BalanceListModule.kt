package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.domain.GetAppVersion
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.BalanceListViewModel

@Module(includes = [ViewModelModule::class])
class BalanceListModule {

    @Provides
    @IntoMap
    @ViewModelKey(BalanceListViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        addressIconGenerator: AddressIconGenerator,
        getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
        selectedFiat: SelectedFiat,
        updatesMixin: UpdatesMixin,
        getAppVersion: GetAppVersion
    ): ViewModel {
        return BalanceListViewModel(
            interactor,
            addressIconGenerator,
            router,
            getAvailableFiatCurrencies,
            selectedFiat,
            updatesMixin,
            getAppVersion
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory,
    ): BalanceListViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BalanceListViewModel::class.java)
    }
}
