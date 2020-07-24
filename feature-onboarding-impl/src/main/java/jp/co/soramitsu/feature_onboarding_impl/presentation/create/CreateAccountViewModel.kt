package jp.co.soramitsu.feature_onboarding_impl.presentation.create

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor

class CreateAccountViewModel(
    private val onboardingInteractor: OnboardingInteractor
): BaseViewModel()