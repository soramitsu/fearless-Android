package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.KaruraApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura.KaruraContributeSubmitter

@Module
class KaruraContributionModule {

    @Provides
    @FeatureScope
    fun provideKaruraApi(
        networkApiCreator: NetworkApiCreator
    ) = networkApiCreator.create(KaruraApi::class.java, KaruraApi.BASE_URL)

    @Provides
    @FeatureScope
    fun provideKaruraInteractor(
        karuraApi: KaruraApi,
        httpExceptionHandler: HttpExceptionHandler,
        accountRepository: AccountRepository,
    ) = KaruraContributeInteractor(karuraApi, httpExceptionHandler, accountRepository, BuildConfig.KARURA_FEALRESS_REFERRAL)

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
