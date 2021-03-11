package jp.co.soramitsu.splash.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface SplashFeatureDependencies {
    fun accountRepository(): AccountRepository
}