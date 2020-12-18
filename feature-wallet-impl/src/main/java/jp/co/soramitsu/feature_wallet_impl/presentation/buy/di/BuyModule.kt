package jp.co.soramitsu.feature_wallet_impl.presentation.buy.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.buy.BuyViewModel

@Module(includes = [ViewModelModule::class])
class BuyModule {

    @Provides
    @IntoMap
    @ViewModelKey(BuyViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        resourceManager: ResourceManager,
        buyTokenRegistry: BuyTokenRegistry,
        router: WalletRouter
    ): ViewModel {
        return BuyViewModel(
            interactor,
            resourceManager,
            buyTokenRegistry,
            router
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): BuyViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(BuyViewModel::class.java)
    }
}