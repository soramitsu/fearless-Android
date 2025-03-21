package jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.moonbeam

import com.google.gson.FieldNamingPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@InstallIn(SingletonComponent::class)
@Module
class MoonbeamContributionModule {

    @Provides
    fun provideMoonbeamApi(networkApiCreator: NetworkApiCreator): MoonbeamApi = networkApiCreator.create(
        service = MoonbeamApi::class.java,
        customFieldNamingPolicy = FieldNamingPolicy.LOWER_CASE_WITH_DASHES
    )

    @Provides
    fun provideMoonbeamInteractor(
        moonbeamApi: MoonbeamApi,
        httpExceptionHandler: HttpExceptionHandler,
        resourceManager: ResourceManager,
        accountRepository: AccountRepository,
        crowdloanRepository: CrowdloanRepository,
        service: ExtrinsicService,
        chainRegistry: ChainRegistry
    ) = MoonbeamContributeInteractor(
        moonbeamApi,
        httpExceptionHandler,
        resourceManager,
        accountRepository,
        crowdloanRepository,
        service,
        chainRegistry
    )

    @Provides
    fun provideMoonbeamSubmitter(
        interactor: MoonbeamContributeInteractor
    ) = MoonbeamContributeSubmitter(interactor)

    @Provides
    @IntoSet
    fun provideMoonbeamFactory(
        submitter: MoonbeamContributeSubmitter,
        moonbeamInteractor: MoonbeamContributeInteractor,
        resourceManager: ResourceManager,
        accountInteractor: AccountInteractor
    ): CustomContributeFactory = MoonbeamContributeFactory(
        submitter,
        moonbeamInteractor,
        resourceManager,
        accountInteractor
    )
}
