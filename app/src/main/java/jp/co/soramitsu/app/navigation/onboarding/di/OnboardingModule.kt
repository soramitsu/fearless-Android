package jp.co.soramitsu.app.navigation.onboarding.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.navigation.onboarding.OnboardingViewModel
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule

@Module(includes = [ViewModelModule::class])
class OnboardingModule {

    @Provides
    @IntoMap
    @ViewModelKey(OnboardingViewModel::class)
    fun provideViewModel(): ViewModel {
        return OnboardingViewModel()
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): OnboardingViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(OnboardingViewModel::class.java)
    }
}