package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class ImportAccountViewmodel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }
}