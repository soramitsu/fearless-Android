package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface OnboardingFeatureDependencies {

    fun accountRepository(): AccountRepository
}