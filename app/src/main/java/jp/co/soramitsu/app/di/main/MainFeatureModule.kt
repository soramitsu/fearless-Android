package jp.co.soramitsu.app.di.main

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.activity.domain.MainInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

@Module
class MainFeatureModule {

    @Provides
    @FeatureScope
    fun provideMainInteractor(
        accountRepository: AccountRepository
    ): MainInteractor {
        return MainInteractor(accountRepository)
    }
}