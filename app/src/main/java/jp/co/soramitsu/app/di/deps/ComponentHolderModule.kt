package jp.co.soramitsu.app.di.deps

import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import jp.co.soramitsu.app.App
import jp.co.soramitsu.app.di.main.MainApi
import jp.co.soramitsu.app.di.main.MainFeatureHolder
import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.core_db.di.DbHolder
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureHolder
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureHolder
import jp.co.soramitsu.splash.di.SplashFeatureApi
import jp.co.soramitsu.splash.di.SplashFeatureHolder

@Module
interface ComponentHolderModule {

    @ApplicationScope
    @Binds
    fun provideFeatureContainer(application: App): FeatureContainer

    @ApplicationScope
    @Binds
    @ClassKey(SplashFeatureApi::class)
    @IntoMap
    fun provideSplashFeatureHolder(splashFeatureHolder: SplashFeatureHolder): FeatureApiHolder

    @ApplicationScope
    @Binds
    @ClassKey(DbApi::class)
    @IntoMap
    fun provideDbFeature(dbHolder: DbHolder): FeatureApiHolder

    @ApplicationScope
    @Binds
    @ClassKey(OnboardingFeatureApi::class)
    @IntoMap
    fun provideOnboardingFeature(onboardingFeatureHolder: OnboardingFeatureHolder): FeatureApiHolder

    @ApplicationScope
    @Binds
    @ClassKey(AccountFeatureApi::class)
    @IntoMap
    fun provideAccountFeature(accountFeatureHolder: AccountFeatureHolder): FeatureApiHolder

    @ApplicationScope
    @Binds
    @ClassKey(MainApi::class)
    @IntoMap
    fun provideMainFeature(accountFeatureHolder: MainFeatureHolder): FeatureApiHolder
}