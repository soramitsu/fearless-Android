package jp.co.soramitsu.onboarding.api.data

interface OnboardingRepository {

    suspend fun getConfig(): Result<OnboardingConfig>

}