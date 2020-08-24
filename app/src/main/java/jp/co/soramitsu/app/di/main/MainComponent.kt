package jp.co.soramitsu.app.di.main

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.activity.di.MainActivityComponent
import jp.co.soramitsu.app.navigation.Navigator
import jp.co.soramitsu.app.navigation.main.di.MainFragmentComponent
import jp.co.soramitsu.app.navigation.onboarding.di.OnboardingComponent
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi

@Component(
    dependencies = [
        MainDependencies::class
    ]
)
@FeatureScope
interface MainComponent {

    fun mainActivityComponentFactory(): MainActivityComponent.Factory

    fun onboardingComponentFactory(): OnboardingComponent.Factory

    fun mainComponentFactory(): MainFragmentComponent.Factory

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