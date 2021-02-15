package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeViewModel

@Module(includes = [ViewModelModule::class])
class WelcomeModule {

    @Provides
    @IntoMap
    @ViewModelKey(WelcomeViewModel::class)
    fun provideViewModel(
        router: OnboardingRouter,
        appLinksProvider: AppLinksProvider,
        shouldShowBack: Boolean,
        networkType: Node.NetworkType?
    ): ViewModel {
        return WelcomeViewModel(shouldShowBack, router, appLinksProvider, networkType)
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): WelcomeViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(WelcomeViewModel::class.java)
    }
}