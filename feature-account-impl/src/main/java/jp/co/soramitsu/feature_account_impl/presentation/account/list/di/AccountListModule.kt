package jp.co.soramitsu.feature_account_impl.presentation.account.list.di

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
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountListViewModel

@Module(includes = [ViewModelModule::class])
class AccountListModule {
    @Provides
    @IntoMap
    @ViewModelKey(AccountListViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        accountListingMixin: AccountListingMixin
    ): ViewModel {
        return AccountListViewModel(interactor, router, accountListingMixin)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AccountListViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AccountListViewModel::class.java)
    }
}