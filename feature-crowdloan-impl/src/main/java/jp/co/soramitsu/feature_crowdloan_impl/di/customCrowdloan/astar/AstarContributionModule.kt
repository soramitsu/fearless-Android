package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.astar

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarContributeSubmitter

@Module
class AstarContributionModule {

    @Provides
    @FeatureScope
    fun provideAstarInteractor(
        httpExceptionHandler: HttpExceptionHandler
    ) = AstarContributeInteractor(httpExceptionHandler, BuildConfig.ASTAR_FEALRESS_REFERRAL)

    @Provides
    @FeatureScope
    fun provideAstarSubmitter(
        interactor: AstarContributeInteractor
    ) = AstarContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideAstarFactory(
        submitter: AstarContributeSubmitter,
        acalaInteractor: AstarContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = AstarContributeFactory(
        submitter,
        acalaInteractor,
        resourceManager
    )
}
