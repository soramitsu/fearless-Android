package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.KaruraContributeSubmitter

@Module
class KaruraContributionModule {

    @Provides
    @FeatureScope
    fun provideKaruraInteractor(

    ) = KaruraContributeInteractor()

    @Provides
    @FeatureScope
    fun provideKaruraSubmitter(
        interactor: KaruraContributeInteractor
    ) = KaruraContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideKaruraFactory(
        submitter: KaruraContributeSubmitter,
        karuraInteractor: KaruraContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = KaruraContributeFactory(submitter, karuraInteractor, resourceManager)
}
