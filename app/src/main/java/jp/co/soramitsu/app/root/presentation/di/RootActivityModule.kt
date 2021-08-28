package jp.co.soramitsu.app.root.presentation.di

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.app.root.presentation.RootViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class RootActivityModule {

    @Provides
    fun provideRootRouter(navigator: Navigator): RootRouter = navigator

    @Provides
    @IntoMap
    @ViewModelKey(RootViewModel::class)
    fun provideViewModel(
        interactor: RootInteractor,
        rootRouter: RootRouter,
        resourceManager: ResourceManager,
        networkStateMixin: NetworkStateMixin,
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>
    ): ViewModel {
        return RootViewModel(
            interactor,
            rootRouter,
            externalRequirementsFlow,
            resourceManager,
            networkStateMixin
        )
    }

    @Provides
    fun provideViewModelCreator(
        activity: AppCompatActivity,
        viewModelFactory: ViewModelProvider.Factory
    ): RootViewModel {
        return ViewModelProvider(activity, viewModelFactory).get(RootViewModel::class.java)
    }
}
