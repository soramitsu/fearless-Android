package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootInteractor(): RootInteractor {
        return RootInteractor()
    }
}