package jp.co.soramitsu.app.di.app

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.di.deps.FeatureHolderManager
import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.scope.ApplicationScope

@Module
class FeatureManagerModule {

    @ApplicationScope
    @Provides
    fun provideFeatureHolderManager(featureApiHolderMap: @JvmSuppressWildcards Map<Class<*>, FeatureApiHolder>): FeatureHolderManager {
        return FeatureHolderManager(featureApiHolderMap)
    }
}