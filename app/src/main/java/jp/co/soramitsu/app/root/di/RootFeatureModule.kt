package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootInteractor(accountRepository: AccountRepository): RootInteractor {
        return RootInteractor(accountRepository)
    }
}