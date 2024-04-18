package jp.co.soramitsu.onboarding.api.domain

import jp.co.soramitsu.onboarding.api.data.OnboardingConfig

interface OnboardingInteractor {

    suspend fun getConfig(): Result<OnboardingConfig>

}
