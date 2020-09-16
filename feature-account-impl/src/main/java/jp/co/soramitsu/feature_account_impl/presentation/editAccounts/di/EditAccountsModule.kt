package jp.co.soramitsu.feature_account_impl.presentation.editAccounts.di

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
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.editAccounts.EditAccountsViewModel

@Module(includes = [ViewModelModule::class])
class EditAccountsModule {
    @Provides
    @IntoMap
    @ViewModelKey(EditAccountsViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        accountListingMixin: AccountListingMixin
    ): ViewModel {
        return EditAccountsViewModel(interactor, router, accountListingMixin)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): EditAccountsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(EditAccountsViewModel::class.java)
    }
}