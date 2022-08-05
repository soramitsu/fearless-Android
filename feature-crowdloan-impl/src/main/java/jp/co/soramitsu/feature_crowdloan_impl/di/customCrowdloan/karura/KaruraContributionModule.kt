package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.karura

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.KaruraApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura.KaruraContributeSubmitter

@InstallIn(SingletonComponent::class)
@Module
class KaruraContributionModule {

    @Provides
    fun provideKaruraApi(
        networkApiCreator: NetworkApiCreator
    ) = networkApiCreator.create(KaruraApi::class.java)

    @Provides
    fun provideKaruraInteractor(
        karuraApi: KaruraApi,
        httpExceptionHandler: HttpExceptionHandler,
        accountRepository: AccountRepository,
    ) = KaruraContributeInteractor(karuraApi, httpExceptionHandler, accountRepository)

    @Provides
    fun provideKaruraSubmitter(
        interactor: KaruraContributeInteractor
    ) = KaruraContributeSubmitter(interactor)

    @Provides
    @IntoSet
    fun provideKaruraFactory(
        submitter: KaruraContributeSubmitter,
        karuraInteractor: KaruraContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = KaruraContributeFactory(submitter, karuraInteractor, resourceManager)
}
