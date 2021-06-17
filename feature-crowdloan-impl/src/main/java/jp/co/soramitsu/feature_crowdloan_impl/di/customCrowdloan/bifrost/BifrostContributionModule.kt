package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.bifrost

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost.BifrostContributeSubmitter

@Module
class BifrostContributionModule {

    @Provides
    @FeatureScope
    fun provideBifrostInteractor() = BifrostContributeInteractor(BuildConfig.BIFROST_FEALRESS_REFERRAL)

    @Provides
    @FeatureScope
    fun provideBifrostSubmitter(
        interactor: BifrostContributeInteractor
    ) = BifrostContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideBifrostFactory(
        submitter: BifrostContributeSubmitter,
        karuraInteractor: BifrostContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = BifrostContributeFactory(
        submitter,
        karuraInteractor,
        resourceManager,
        BuildConfig.BIFROST_TERMS_LINKS
    )
}
