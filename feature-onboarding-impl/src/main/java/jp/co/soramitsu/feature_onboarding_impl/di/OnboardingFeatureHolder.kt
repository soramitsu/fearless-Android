package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import javax.inject.Inject

@ApplicationScope
class OnboardingFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val onboardingRouter: OnboardingRouter
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val onboardingFeatureDependencies = DaggerOnboardingFeatureComponent_OnboardingFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .build()
        return DaggerOnboardingFeatureComponent.factory()
            .create(onboardingRouter, onboardingFeatureDependencies)
    }
}