package jp.co.soramitsu.feature_onboarding_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor

class OnboardingInteractorImpl : OnboardingInteractor {

    override fun saveAccountName(accountName: String): Completable {
        return Completable.complete()
    }

    override fun getTermsAddress(): Single<String> {
        return Single.just("https://sora.org/terms")
    }

    override fun getPrivacyAddress(): Single<String> {
        return Single.just("https://vk.com")
    }
}