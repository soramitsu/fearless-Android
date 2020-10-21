package jp.co.soramitsu.feature_wallet_impl.presentation.receive.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveViewModel

@Module(includes = [ViewModelModule::class])
class ReceiveModule {

    @Provides
    @IntoMap
    @ViewModelKey(ReceiveViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor
    ): ViewModel {
        return ReceiveViewModel(interactor)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ReceiveViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ReceiveViewModel::class.java)
    }
}