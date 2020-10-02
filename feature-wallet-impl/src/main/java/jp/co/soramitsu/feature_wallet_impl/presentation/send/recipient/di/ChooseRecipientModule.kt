package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.ChooseRecipientViewModel

@Module(includes = [ViewModelModule::class])
class ChooseRecipientModule {

    @Provides
    @IntoMap
    @ViewModelKey(ChooseRecipientViewModel::class)
    fun provideViewModel(
        interactor: WalletInteractor,
        router: WalletRouter,
        resourceManager: ResourceManager,
        iconGenerator: IconGenerator
    ): ViewModel {
        return ChooseRecipientViewModel(interactor, router, resourceManager, iconGenerator)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): ChooseRecipientViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(ChooseRecipientViewModel::class.java)
    }
}