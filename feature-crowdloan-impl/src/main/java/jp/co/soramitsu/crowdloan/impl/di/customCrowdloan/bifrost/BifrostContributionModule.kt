package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.bifrost

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.crowdloan.impl.data.network.api.bifrost.BifrostApi
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.bifrost.BifrostContributeSubmitter

@InstallIn(SingletonComponent::class)
@Module
class BifrostContributionModule {

    @Provides
    fun provideBifrostApi(networkApiCreator: NetworkApiCreator): BifrostApi {
        return networkApiCreator.create(BifrostApi::class.java, customBaseUrl = BifrostApi.BASE_URL)
    }

    @Provides
    fun provideBifrostInteractor(
        bifrostApi: BifrostApi,
        httpExceptionHandler: HttpExceptionHandler
    ) = BifrostContributeInteractor(bifrostApi, httpExceptionHandler)

    @Provides
    fun provideBifrostSubmitter(
        interactor: BifrostContributeInteractor
    ) = BifrostContributeSubmitter(interactor)

    @Provides
    @IntoSet
    fun provideBifrostFactory(
        submitter: BifrostContributeSubmitter,
        karuraInteractor: BifrostContributeInteractor,
        resourceManager: ResourceManager
    ): CustomContributeFactory = BifrostContributeFactory(
        submitter,
        karuraInteractor,
        resourceManager
    )
}
