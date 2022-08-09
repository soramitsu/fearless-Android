package jp.co.soramitsu.featureonboardingimpl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featureonboardingapi.domain.OnboardingInteractor
import jp.co.soramitsu.featureonboardingimpl.domain.OnboardingInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
class OnboardingFeatureModule {

    @Provides
    fun provideOnboardingInteractor(): OnboardingInteractor {
        return OnboardingInteractorImpl()
    }
}
