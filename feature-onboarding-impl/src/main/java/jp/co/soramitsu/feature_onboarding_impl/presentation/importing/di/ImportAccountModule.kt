package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import jp.co.soramitsu.common.di.viewmodel.ViewModelKey
import jp.co.soramitsu.common.di.viewmodel.ViewModelModule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.ImportAccountViewmodel

@Module(includes = [ViewModelModule::class])
class ImportAccountModule {

    @Provides
    @IntoMap
    @ViewModelKey(ImportAccountViewmodel::class)
    fun provideViewModel(interactor: OnboardingInteractor, router: OnboardingRouter, resourceManager: ResourceManager): ViewModel {
        return ImportAccountViewmodel(interactor, router, resourceManager)
    }

    @Provides
    fun provideViewModelCreator(fragment: Fragment, viewModelFactory: ViewModelProvider.Factory): ImportAccountViewmodel {
        return ViewModelProvider(fragment, viewModelFactory).get(ImportAccountViewmodel::class.java)
    }
}