package jp.co.soramitsu.app.root.presentation.main.di

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.presentation.main.MainViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MainFragmentModule {

    @Provides
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    fun provideViewModel(
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
        interactor: RootInteractor
    ): ViewModel {
        return MainViewModel(interactor, externalRequirementsFlow)
    }

    @Provides
    fun provideViewModelCreator(
        activity: FragmentActivity,
        viewModelFactory: ViewModelProvider.Factory
    ): MainViewModel {
        return ViewModelProvider(activity, viewModelFactory).get(MainViewModel::class.java)
    }
}
