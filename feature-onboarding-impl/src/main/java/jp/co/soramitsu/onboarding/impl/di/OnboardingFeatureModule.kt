package jp.co.soramitsu.onboarding.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingRepository
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.data.OnboardingConfigApi
import jp.co.soramitsu.onboarding.impl.data.OnboardingRepositoryImpl
import jp.co.soramitsu.onboarding.impl.data.deserializer
import jp.co.soramitsu.onboarding.impl.domain.OnboardingInteractorImpl
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class OnboardingFeatureModule {

    @Provides
    @Singleton
    fun provideOnboardingConfigApi(networkApiCreator: NetworkApiCreator): OnboardingConfigApi =
        networkApiCreator.create(
            OnboardingConfigApi::class.java,
            typeAdapters = mapOf(
                OnboardingConfig::class.java to OnboardingConfig.deserializer,
                OnboardingConfig.OnboardingConfigItem::class.java to OnboardingConfig.OnboardingConfigItem.deserializer,
                OnboardingConfig.OnboardingConfigItem.Variants::class.java to OnboardingConfig.OnboardingConfigItem.Variants.deserializer,
                OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo::class.java to OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.deserializer,
                OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo::class.java to OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo.deserializer,
            )
        )

    @Provides
    fun provideOnboardingRepository(
        onboardingConfigApi: OnboardingConfigApi
    ): OnboardingRepository {
        return OnboardingRepositoryImpl(
            onboardingConfigApi
        )
    }

    @Provides
    fun provideOnboardingInteractor(
        onboardingRepository: OnboardingRepository,
        preferences: Preferences
    ): OnboardingInteractor {
        return OnboardingInteractorImpl(
            onboardingRepository,
            preferences
        )
    }

    @Provides
    fun provideImportPreInstalledAccount(
        accountRepository: AccountRepository,
        preferences: Preferences,
        remoteConfigFetcher: RemoteConfigFetcher
    ) = PendulumPreInstalledAccountsScenario(accountRepository, preferences, remoteConfigFetcher)
}
