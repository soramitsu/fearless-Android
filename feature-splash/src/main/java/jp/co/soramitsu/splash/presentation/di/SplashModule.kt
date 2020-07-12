package jp.co.soramitsu.splash.presentation.di

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.splash.presentation.SplashViewModel

@Module(includes = [ViewModelModule::class])
class SplashModule {

    @Provides
    internal fun provideScannerViewModel(activity: AppCompatActivity, factory: ViewModelProvider.Factory): SplashViewModel {
        return ViewModelProviders.of(activity, factory).get(SplashViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(SplashViewModel::class)
    fun provideSignInViewModel(router: SplashRouter): ViewModel {
        return SplashViewModel(router)
    }
}