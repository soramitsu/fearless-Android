package jp.co.soramitsu.onboarding.api.domain

import jp.co.soramitsu.onboarding.api.data.OnboardingConfig

interface OnboardingInteractor {

    suspend fun getConfig(): Result<OnboardingConfig>
    suspend fun getAppVersionSupportedConfig(): Result<OnboardingConfig.OnboardingConfigItem?>

    fun getWelcomeSlidesShownVersion(): String?
    fun saveWelcomeSlidesShownVersion(version: String)
    fun shouldShowWelcomeSlides(version: String): Boolean
}
