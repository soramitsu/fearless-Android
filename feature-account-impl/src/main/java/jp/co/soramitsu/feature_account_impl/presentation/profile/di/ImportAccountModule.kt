package jp.co.soramitsu.feature_account_impl.presentation.profile.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountViewModel

@Module(includes = [ViewModelModule::class])
class ProfileModule {

    @Provides
    @IntoMap
    @ViewModelKey(ProfileModule::class)
    fun provideViewModel(interactor: AccountInteractor, router: AccountRouter, resourceManager: ResourceManager): ViewModel {
        return ProfileModule(interactor, router, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ProfileModule {
        return ViewModelProvider(fragment, viewModelFactory).get(ProfileModule::class.java)
    }
}