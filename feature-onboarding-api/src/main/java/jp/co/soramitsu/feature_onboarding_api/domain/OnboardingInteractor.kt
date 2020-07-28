package jp.co.soramitsu.feature_onboarding_api.domain

import io.reactivex.Completable
import io.reactivex.Single

interface OnboardingInteractor {

    fun saveAccountName(accountName: String): Completable

    fun getTermsAddress(): Single<String>

    fun getPrivacyAddress(): Single<String>
}