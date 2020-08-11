package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class WelcomeViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    fun createAccountClicked() {
        router.openCreateAccount()
    }

    fun importAccountClicked() {
        router.openImportAccountScreen()
    }

    fun termsClicked() {
        router.openTermsScreen()
    }

    fun privacyClicked() {
        router.openPrivacyScreen()
    }
}