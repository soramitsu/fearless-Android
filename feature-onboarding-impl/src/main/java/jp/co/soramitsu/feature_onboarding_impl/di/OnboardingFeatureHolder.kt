package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import javax.inject.Inject

@ApplicationScope
class OnboardingFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val onboardingFeatureDependencies = DaggerOnboardingFeatureComponent_OnboardingFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .build()
        return DaggerOnboardingFeatureComponent.builder()
            .onboardingFeatureDependencies(onboardingFeatureDependencies)
            .build()
    }
}