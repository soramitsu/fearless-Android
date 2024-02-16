package jp.co.soramitsu.onboarding.impl.domain

import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingRepository
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor

class OnboardingInteractorImpl(
    private val onboardingRepository: OnboardingRepository
): OnboardingInteractor {

    override suspend fun getConfig(): Result<OnboardingConfig> {
        return onboardingRepository.getConfig()
    }

}
