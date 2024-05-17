package jp.co.soramitsu.onboarding.impl.domain

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingRepository
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor

class OnboardingInteractorImpl(
    private val onboardingRepository: OnboardingRepository,
    private val preferences: Preferences
): OnboardingInteractor {

    companion object {
        private const val PREFS_SHOWN_WELCOME_VERSION = "prefs_shown_welcome_version"
    }

    override suspend fun getConfig(): Result<OnboardingConfig> {
        return onboardingRepository.getConfig()
    }

    override fun getWelcomeSlidesShownVersion(): String? {
        return preferences.getString(PREFS_SHOWN_WELCOME_VERSION)
    }

    override fun saveWelcomeSlidesShownVersion(version: String) {
        preferences.putString(PREFS_SHOWN_WELCOME_VERSION, version)
    }

    override fun shouldShowWelcomeSlides(version: String): Boolean {
        return version != getWelcomeSlidesShownVersion()
    }
}
