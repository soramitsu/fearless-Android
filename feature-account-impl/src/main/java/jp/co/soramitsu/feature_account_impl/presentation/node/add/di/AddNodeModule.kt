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
import jp.co.soramitsu.feature_account_api.domain.interfaces.NodesSettingsScenario
import jp.co.soramitsu.feature_account_impl.domain.NodeHostValidator
import jp.co.soramitsu.feature_account_impl.domain.NodesSettingsScenarioImpl
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.node.add.AddNodeViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class AddNodeModule {

    @Provides
    @IntoMap
    @ViewModelKey(AddNodeViewModel::class)
    fun provideViewModel(
        nodesSettingsScenario: NodesSettingsScenario,
        router: AccountRouter,
        nodeHostValidator: NodeHostValidator,
        resourceManager: ResourceManager,
        chainId: String
    ): ViewModel {
        return AddNodeViewModel(nodesSettingsScenario, router, nodeHostValidator, resourceManager, chainId)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): AddNodeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(AddNodeViewModel::class.java)
    }

    @Provides
    fun provideNodesSettingsScenario(chainRegistry: ChainRegistry): NodesSettingsScenario {
        return NodesSettingsScenarioImpl(chainRegistry)
    }
}
