package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter

@Module
class MoonbeamContributionModule {

    @Provides
    @FeatureScope
    fun provideMoonbeamApi(networkApiCreator: NetworkApiCreator): MoonbeamApi {
        return networkApiCreator.create(MoonbeamApi::class.java, customBaseUrl = MoonbeamApi.BASE_URL_TEST)
    }

    @Provides
    @FeatureScope
    fun provideMoonbeamInteractor(
        acalaApi: MoonbeamApi,
        httpExceptionHandler: HttpExceptionHandler
    ) = MoonbeamContributeInteractor(acalaApi, httpExceptionHandler, BuildConfig.MOONBEAM_FEALRESS_REFERRAL)

    @Provides
    @FeatureScope
    fun provideMoonbeamSubmitter(
        interactor: MoonbeamContributeInteractor
    ) = MoonbeamContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideMoonbeamFactory(
        submitter: MoonbeamContributeSubmitter,
        acalaInteractor: MoonbeamContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = MoonbeamContributeFactory(
        submitter,
        acalaInteractor,
        resourceManager
    )
}
