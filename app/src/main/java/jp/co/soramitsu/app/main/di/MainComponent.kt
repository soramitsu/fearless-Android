package jp.co.soramitsu.app.main.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.main.navigation.Navigator
import jp.co.soramitsu.app.main.navigation.main.di.MainFragmentComponent
import jp.co.soramitsu.app.main.navigation.onboarding.di.OnboardingComponent
import jp.co.soramitsu.app.main.presentation.di.MainActivityComponent
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi

@Component(
    dependencies = [
        MainDependencies::class
    ],
    modules = [
        MainFeatureModule::class
    ]
)
@FeatureScope
interface MainComponent {

    fun mainActivityComponentFactory(): MainActivityComponent.Factory

    fun onboardingComponentFactory(): OnboardingComponent.Factory

    fun mainFragmentComponentFactory(): MainFragmentComponent.Factory

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance navigator: Navigator,
            deps: MainDependencies
        ): MainComponent
    }

    @Component(
        dependencies = [
            AccountFeatureApi::class
        ]
    )
    interface MainFeatureDependenciesComponent : MainDependencies
}