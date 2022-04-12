package jp.co.soramitsu.splash.di

import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface SplashFeatureDependencies {
    fun accountRepository(): AccountRepository

    fun shouldShowEducationalStoriesUseCase(): ShouldShowEducationalStoriesUseCase

    fun getEducationalStoriesUseCase(): GetEducationalStoriesUseCase
}
