package jp.co.soramitsu.onboarding.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.domain.OnboardingInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
class OnboardingFeatureModule {

    @Provides
    fun provideOnboardingInteractor(): OnboardingInteractor {
        return OnboardingInteractorImpl()
    }

    @Provides
    fun provideImportPreInstalledAccount(
        accountRepository: AccountRepository,
        preferences: Preferences,
        remoteConfigFetcher: RemoteConfigFetcher
    ) =
        PendulumPreInstalledAccountsScenario(accountRepository, preferences, remoteConfigFetcher)
}
