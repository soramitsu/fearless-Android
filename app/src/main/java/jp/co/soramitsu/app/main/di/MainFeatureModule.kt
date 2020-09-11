package jp.co.soramitsu.app.main.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.main.domain.MainInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope

@Module
class MainFeatureModule {

    @Provides
    @FeatureScope
    fun provideMainInteractor(): MainInteractor {
        return MainInteractor()
    }
}