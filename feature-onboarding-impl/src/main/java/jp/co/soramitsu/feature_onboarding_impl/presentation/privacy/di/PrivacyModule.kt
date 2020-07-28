package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.di

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
import jp.co.soramitsu.feature_onboarding_impl.presentation.privacy.PrivacyViewModel

@Module(includes = [ViewModelModule::class])
class PrivacyModule {

    @Provides
    @IntoMap
    @ViewModelKey(PrivacyViewModel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter): ViewModel {
        return PrivacyViewModel(interactor, router)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): PrivacyViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(PrivacyViewModel::class.java)
    }
}