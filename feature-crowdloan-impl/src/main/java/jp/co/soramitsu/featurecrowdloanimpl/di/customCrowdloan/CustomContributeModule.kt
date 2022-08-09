package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.acala.AcalaContributionModule
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.astar.AstarContributionModule
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.bifrost.BifrostContributionModule
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.interlay.InterlayContributionModule
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.karura.KaruraContributionModule
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.moonbeam.MoonbeamContributionModule

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
