package jp.co.soramitsu.feature_onboarding_api.domain

import io.reactivex.Completable

interface OnboardingInteractor {

    fun saveAccountName(accountName: String): Completable
}