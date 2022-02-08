package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets.ManageAssetsViewModel

@Module(includes = [ViewModelModule::class])
class ManageAssetsModule {

    @Provides
    @IntoMap
    @ViewModelKey(ManageAssetsViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
    ): ViewModel {
        return ManageAssetsViewModel(
            interactor,
            router
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ManageAssetsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ManageAssetsViewModel::class.java)
    }
}
