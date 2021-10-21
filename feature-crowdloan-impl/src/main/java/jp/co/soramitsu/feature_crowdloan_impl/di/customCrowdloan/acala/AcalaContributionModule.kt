package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.acala

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributeSubmitter

@Module
class AcalaContributionModule {

    @Provides
    @FeatureScope
    fun provideAcalaApi(networkApiCreator: NetworkApiCreator): AcalaApi {
        return networkApiCreator.create(AcalaApi::class.java, customBaseUrl = AcalaApi.BASE_URL)
    }

    @Provides
    @FeatureScope
    fun provideAcalaInteractor(
        acalaApi: AcalaApi,
        httpExceptionHandler: HttpExceptionHandler
    ) = AcalaContributeInteractor(acalaApi, httpExceptionHandler, BuildConfig.ACALA_FEALRESS_REFERRAL)

    @Provides
    @FeatureScope
    fun provideAcalaSubmitter(
        interactor: AcalaContributeInteractor
    ) = AcalaContributeSubmitter(interactor)

    @Provides
    @FeatureScope
    @IntoSet
    fun provideAcalaFactory(
        submitter: AcalaContributeSubmitter,
        acalaInteractor: AcalaContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = AcalaContributeFactory(
        submitter,
        acalaInteractor,
        resourceManager
    )
}
