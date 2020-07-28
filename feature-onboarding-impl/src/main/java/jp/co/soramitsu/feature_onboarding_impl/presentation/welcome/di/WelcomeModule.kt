package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeViewModel

@Module(includes = [ViewModelModule::class])
class WelcomeModule {

    @Provides
    @IntoMap
    @ViewModelKey(WelcomeViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter): ViewModel {
        return WelcomeViewModel(interactor, router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): WelcomeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(WelcomeViewModel::class.java)
    }
}