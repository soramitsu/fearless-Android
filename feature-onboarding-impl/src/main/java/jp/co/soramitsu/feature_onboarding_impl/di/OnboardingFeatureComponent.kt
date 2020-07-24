package jp.co.soramitsu.feature_onboarding_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.presentation.create.di.CreateAccountComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.di.WelcomeComponent

@Component(
    dependencies = [
        OnboardingFeatureDependencies::class
    ],
    modules = [
        OnboardingFeatureModule::class
    ]
)
@FeatureScope
interface OnboardingFeatureComponent : OnboardingFeatureApi {

    fun welcomeComponentFactory(): WelcomeComponent.Factory

    fun createAccountComponentFactory(): CreateAccountComponent.Factory

    @Component(
        dependencies = [
            CommonApi::class
        ]
    )
    interface OnboardingFeatureDependenciesComponent : OnboardingFeatureDependencies
}