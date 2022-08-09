package jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.acala

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featurecrowdloanimpl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.featurecrowdloanimpl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.acala.AcalaContributeSubmitter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@InstallIn(SingletonComponent::class)
@Module
class AcalaContributionModule {

    @Provides
    fun provideAcalaApi(networkApiCreator: NetworkApiCreator): AcalaApi {
        return networkApiCreator.create(AcalaApi::class.java)
    }

    @Provides
    fun provideAcalaInteractor(
        acalaApi: AcalaApi,
        httpExceptionHandler: HttpExceptionHandler,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry
    ) = AcalaContributeInteractor(acalaApi, httpExceptionHandler, accountRepository, chainRegistry)

    @Provides
    fun provideAcalaSubmitter(
        interactor: AcalaContributeInteractor
    ) = AcalaContributeSubmitter(interactor)

    @Provides
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
