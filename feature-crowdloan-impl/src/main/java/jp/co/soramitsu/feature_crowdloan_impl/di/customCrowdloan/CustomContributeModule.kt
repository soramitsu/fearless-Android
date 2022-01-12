package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.acala.AcalaContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.astar.AstarContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.bifrost.BifrostContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.interlay.InterlayContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura.KaruraContributionModule
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam.MoonbeamContributionModule

@Module(
    includes = [
        KaruraContributionModule::class,
        BifrostContributionModule::class,
        InterlayContributionModule::class,
        AcalaContributionModule::class,
        AstarContributionModule::class,
        MoonbeamContributionModule::class
    ]
)
class CustomContributeModule {

    @Provides
    @FeatureScope
    fun provideCustomContributionManager(
        factories: @JvmSuppressWildcards Set<CustomContributeFactory>
    ) = CustomContributeManager(factories)
}
