package jp.co.soramitsu.feature_onboarding_api.domain

import io.reactivex.Single

interface OnboardingInteractor {

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>
}