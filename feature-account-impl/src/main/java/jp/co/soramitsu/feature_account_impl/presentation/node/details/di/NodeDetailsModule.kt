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
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.domain.NodesSettingsScenarioImpl
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class NodeDetailsModule {

    @Provides
    @IntoMap
    @ViewModelKey(NodeDetailsViewModel::class)
    fun provideViewModel(
        nodesSettingsScenario: NodesSettingsScenario,
        router: AccountRouter,
        clipboardManager: ClipboardManager,
        resourceManager: ResourceManager,
        payload: NodeDetailsPayload
    ): ViewModel {
        return NodeDetailsViewModel(nodesSettingsScenario, router, clipboardManager, resourceManager, payload)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): NodeDetailsViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(NodeDetailsViewModel::class.java)
    }

    @Provides
    fun provideNodesSettingsScenario(chainRegistry: ChainRegistry): NodesSettingsScenario {
        return NodesSettingsScenarioImpl(chainRegistry)
    }
}
