package jp.co.soramitsu.app.navigation.main.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.navigation.main.MainViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(includes = [ViewModelModule::class])
class MainModule {

    @Provides
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    fun provideViewModel(): ViewModel {
        return MainViewModel()
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): MainViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(MainViewModel::class.java)
    }
}