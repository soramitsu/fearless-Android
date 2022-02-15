package jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.di.modules.Caching
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.AccountsForExportPayload
import jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.AccountsForExportViewModel

@Module(includes = [ViewModelModule::class])
class AccountsForExportModule {

    @Provides
    @IntoMap
    @ViewModelKey(AccountsForExportViewModel::class)
    fun provideViewModel(
        interactor: AccountDetailsInteractor,
        router: AccountRouter,
        resourceManager: ResourceManager,
        @Caching
        iconGenerator: AddressIconGenerator,
        payload: AccountsForExportPayload,
    ): ViewModel {
        return AccountsForExportViewModel(
            interactor,
            router,
            iconGenerator,
            resourceManager,
            payload
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AccountsForExportViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AccountsForExportViewModel::class.java)
    }
}
