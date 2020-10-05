package jp.co.soramitsu.feature_account_impl.presentation.node.add.di

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
import jp.co.soramitsu.feature_account_impl.domain.NodeHostValidator
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.add.AddNodeViewModel

@Module(includes = [ViewModelModule::class])
class AddNodeModule {

    @Provides
    @IntoMap
    @ViewModelKey(AddNodeViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        nodeHostValidator: NodeHostValidator,
        resourceManager: ResourceManager
    ): ViewModel {
        return AddNodeViewModel(interactor, router, nodeHostValidator, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AddNodeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AddNodeViewModel::class.java)
    }
}