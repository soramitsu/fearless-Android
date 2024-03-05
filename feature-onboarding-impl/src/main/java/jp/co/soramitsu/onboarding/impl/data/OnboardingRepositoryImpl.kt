package jp.co.soramitsu.onboarding.impl.data

import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingRepository

class OnboardingRepositoryImpl(
    private val onboardingConfigApi: OnboardingConfigApi
): OnboardingRepository {

    override suspend fun getConfig(): Result<OnboardingConfig> {
        return runCatching { onboardingConfigApi.getConfig() }
    }

}