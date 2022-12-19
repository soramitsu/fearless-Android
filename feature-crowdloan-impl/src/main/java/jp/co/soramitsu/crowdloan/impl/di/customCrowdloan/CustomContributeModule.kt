package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.acala.AcalaContributionModule
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.astar.AstarContributionModule
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.bifrost.BifrostContributionModule
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.interlay.InterlayContributionModule
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.karura.KaruraContributionModule
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.moonbeam.MoonbeamContributionModule

@InstallIn(SingletonComponent::class)
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
    fun provideCustomContributionManager(
        factories: @JvmSuppressWildcards Set<CustomContributeFactory>
    ) = CustomContributeManager(factories)
}
