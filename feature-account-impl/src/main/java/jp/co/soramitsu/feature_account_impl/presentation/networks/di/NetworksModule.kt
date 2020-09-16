package jp.co.soramitsu.feature_account_impl.presentation.networks.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.accounts.AccountsViewModel
import jp.co.soramitsu.feature_account_impl.presentation.networks.NetworksViewModel

@Module(includes = [ViewModelModule::class])
class NetworksModule {

    @Provides
    @IntoMap
    @ViewModelKey(NetworksViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter
    ): ViewModel {
        return NetworksViewModel(interactor, router)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): NetworksViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(NetworksViewModel::class.java)
    }
}