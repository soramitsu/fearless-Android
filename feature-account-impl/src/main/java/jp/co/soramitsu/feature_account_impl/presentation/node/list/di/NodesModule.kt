package jp.co.soramitsu.feature_account_impl.presentation.node.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.list.NodesViewModel
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.impl.NodeListingProvider

@Module(includes = [ViewModelModule::class])
class NodesModule {

    @Provides
    fun provideNodeListingMixin(
        interactor: AccountInteractor,
        resourceManager: ResourceManager
    ): NodeListingMixin = NodeListingProvider(interactor, resourceManager)

    @Provides
    @IntoMap
    @ViewModelKey(NodesViewModel::class)
    fun provideViewModel(
        interactor: AccountInteractor,
        router: AccountRouter,
        nodeListingMixin: NodeListingMixin,
        addressIconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager
    ): ViewModel {
        return NodesViewModel(interactor, router, nodeListingMixin, addressIconGenerator, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): NodesViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(NodesViewModel::class.java)
    }
}