package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor

class OnboardingInteractorImpl : OnboardingInteractor {

    override fun saveAccountName(accountName: String): Completable {
        return Completable.complete()
    }
}