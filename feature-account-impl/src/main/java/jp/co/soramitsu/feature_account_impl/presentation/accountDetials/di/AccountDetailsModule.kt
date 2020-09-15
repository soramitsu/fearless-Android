package jp.co.soramitsu.feature_account_impl.presentation.accountDetials.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.accountDetials.AccountDetailsViewModel

@Module(includes = [ViewModelModule::class])
class AccountDetailsModule {
    @Provides
    @IntoMap
    @ViewModelKey(AccountDetailsViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        accountAddress: String,
        router: AccountRouter,
        clipboardManager: ClipboardManager,
        resourceManager: ResourceManager
    ): ViewModel {
        return AccountDetailsViewModel(interactor, router, clipboardManager, resourceManager, accountAddress)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AccountDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AccountDetailsViewModel::class.java)
    }
}