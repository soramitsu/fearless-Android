package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam

import com.google.gson.FieldNamingPolicy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module
class MoonbeamContributionModule {

    @Provides
    @FeatureScope
    fun provideMoonbeamApi(networkApiCreator: NetworkApiCreator): MoonbeamApi = networkApiCreator.create(
        service = MoonbeamApi::class.java,
        customFieldNamingPolicy = FieldNamingPolicy.LOWER_CASE_WITH_DASHES
    )

    @Provides
    @FeatureScope
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
        resourceManager: ResourceManager,
        accountUseCase: SelectedAccountUseCase
    ): CustomContributeFactory = MoonbeamContributeFactory(
        submitter,
        moonbeamInteractor,
        resourceManager,
        accountUseCase
    )
}
