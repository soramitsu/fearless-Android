package jp.co.soramitsu.splash.presentation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.splash.presentation.SplashViewModel

@Module(includes = [ViewModelModule::class])
class SplashModule {

    @Provides
    internal fun provideScannerViewModel(fragment: Fragment, factory: ViewModelProvider.Factory): SplashViewModel {
        return ViewModelProvider(fragment, factory).get(SplashViewModel::class.java)
    }

    @Provides
    @IntoMap
    @ViewModelKey(SplashViewModel::class)
    fun provideSignInViewModel(accountRepository: AccountRepository, router: SplashRouter): ViewModel {
        return SplashViewModel(router, accountRepository)
    }
}