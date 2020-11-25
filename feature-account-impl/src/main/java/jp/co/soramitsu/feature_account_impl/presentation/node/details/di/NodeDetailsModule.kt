package jp.co.soramitsu.feature_account_impl.presentation.node.details.di

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
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsViewModel

@Module(includes = [ViewModelModule::class])
class NodeDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(NodeDetailsViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        nodeId: Int,
        isSelected: Boolean,
        clipboardManager: ClipboardManager,
        resourceManager: ResourceManager
    ): ViewModel {
        return NodeDetailsViewModel(interactor, router, nodeId, isSelected, clipboardManager, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): NodeDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(NodeDetailsViewModel::class.java)
    }
}