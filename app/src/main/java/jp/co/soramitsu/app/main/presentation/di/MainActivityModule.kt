package jp.co.soramitsu.app.main.presentation.di

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.main.domain.MainInteractor
import jp.co.soramitsu.app.main.presentation.MainViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class MainActivityModule {

    @Provides
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    fun provideViewModel(interactor: MainInteractor): ViewModel {
        return MainViewModel(interactor)
    }

    @Provides
    fun provideViewModelCreator(activity: AppCompatActivity, viewModelFactory: ViewModelProvider.Factory): MainViewModel {
        return ViewModelProvider(activity, viewModelFactory).get(MainViewModel::class.java)
    }
}