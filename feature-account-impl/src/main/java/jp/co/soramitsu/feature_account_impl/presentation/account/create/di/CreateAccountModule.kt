package jp.co.soramitsu.feature_account_impl.presentation.account.create.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.create.CreateAccountViewModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl.NetworkChooser

@Module(includes = [ViewModelModule::class])
class CreateAccountModule {

    @Provides
    fun provideNetworkChooserMixin(interactor: AccountInteractor, networkType: Node.NetworkType?): NetworkChooserMixin {
        return NetworkChooser(interactor, networkType)
    }

    @Provides
    @IntoMap
    @ViewModelKey(CreateAccountViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        networkChooserMixin: NetworkChooserMixin
    ): ViewModel {
        return CreateAccountViewModel(interactor, router, networkChooserMixin)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): CreateAccountViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(CreateAccountViewModel::class.java)
    }
}