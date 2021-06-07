package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura.KaruraContributionModule

@Module(
    includes = [
        KaruraContributionModule::class
    ]
)
class CustomContributeModule {

    @Provides
    @FeatureScope
    fun provideCustomContributionManager(
        factories: @JvmSuppressWildcards Set<CustomContributeFactory>
    ) = CustomContributeManager(factories)
}
