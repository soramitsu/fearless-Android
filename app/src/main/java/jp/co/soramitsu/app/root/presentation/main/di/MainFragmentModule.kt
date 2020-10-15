package jp.co.soramitsu.app.root.presentation.main.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.root.presentation.main.MainViewModel
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MainFragmentModule {

    @Provides
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    fun provideViewModel(connectionManager: ConnectionManager): ViewModel {
        return MainViewModel(connectionManager)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): MainViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(MainViewModel::class.java)
    }
}