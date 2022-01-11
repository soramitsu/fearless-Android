package jp.co.soramitsu.feature_account_impl.presentation.account.details.di

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
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountDetailsViewModel

@Module(includes = [ViewModelModule::class])
class AccountDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(AccountDetailsViewModel::class)
    fun provideViewModel(
        interactor: AccountDetailsInteractor,
        router: AccountRouter,
        resourceManager: ResourceManager,
        @Caching
        iconGenerator: AddressIconGenerator,
        metaId: Long,
    ): ViewModel {
        return AccountDetailsViewModel(
            interactor,
            router,
            iconGenerator,
            resourceManager,
            metaId
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AccountDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AccountDetailsViewModel::class.java)
    }
}
