package jp.co.soramitsu.feature_account_impl.presentation.node.list.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.domain.NodesSettingsScenarioImpl
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.list.NodesViewModel
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.api.NodeListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.node.mixin.impl.NodeListingProvider
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class NodesModule {

    @Provides
    fun provideNodeListingMixin(
        nodesSettingsScenario: NodesSettingsScenario,
        resourceManager: ResourceManager,
        chainId: String
    ): NodeListingMixin = NodeListingProvider(nodesSettingsScenario, resourceManager, chainId)

    @Provides
    @IntoMap
    @ViewModelKey(NodesViewModel::class)
    fun provideViewModel(
        router: AccountRouter,
        nodeListingMixin: NodeListingMixin,
        resourceManager: ResourceManager,
        chainId: String,
        nodesSettingsScenario: NodesSettingsScenario
    ): ViewModel {
        return NodesViewModel(router, nodeListingMixin, resourceManager, chainId, nodesSettingsScenario)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): NodesViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(NodesViewModel::class.java)
    }

    @Provides
    fun provideNodesSettingsScenario(chainRegistry: ChainRegistry): NodesSettingsScenario {
        return NodesSettingsScenarioImpl(chainRegistry)
    }
}
