package jp.co.soramitsu.splash.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.splash.presentation.di.SplashComponent

@Component(
    dependencies = [
        SplashFeatureDependencies::class
    ],
    modules = [
        SplashFeatureModule::class
    ]
)
@FeatureScope
interface SplashFeatureComponent : SplashFeatureApi {

    fun splashComponentFactory(): SplashComponent.Factory

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun router(splashRouter: SplashRouter): Builder

        fun withDependencies(deps: SplashFeatureDependencies): Builder

        fun build(): SplashFeatureComponent
    }

    @Component(
        dependencies = [
            CommonApi::class,
            AccountFeatureApi::class
        ]
    )
    interface SplashFeatureDependenciesComponent : SplashFeatureDependencies
}