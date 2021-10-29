package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator

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
        moonbeamApi: MoonbeamApi,
        httpExceptionHandler: HttpExceptionHandler,
        feeEstimator: FeeEstimator,
        resourceManager: ResourceManager,
        accountRepository: AccountRepository,
        service: ExtrinsicService,
        property: SuspendableProperty<RuntimeSnapshot>
    ) = MoonbeamContributeInteractor(
        moonbeamApi,
        httpExceptionHandler,
        resourceManager,
        BuildConfig.MOONBEAM_FEALRESS_REFERRAL,
        feeEstimator,
        accountRepository,
        service,
        property
    )

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
        moonbeamInteractor: MoonbeamContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = MoonbeamContributeFactory(
        submitter,
        moonbeamInteractor,
        resourceManager
    )
}
