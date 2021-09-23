package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.ParachainMetadataApi
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeModule
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

@Module(
    includes = [
        CustomContributeModule::class
    ]
)
class CrowdloanFeatureModule {

    @Provides
    @FeatureScope
    fun crowdloanRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        crowdloanMetadataApi: ParachainMetadataApi,
        chainRegistry: ChainRegistry,
        accountRepository: AccountRepository,
    ): CrowdloanRepository = CrowdloanRepositoryImpl(
        remoteStorageSource,
        accountRepository,
        chainRegistry,
        crowdloanMetadataApi
    )

    @Provides
    @FeatureScope
    fun provideCrowdloanInteractor(
        accountRepository: AccountRepository,
        crowdloanRepository: CrowdloanRepository,
        sharedState: CrowdloanSharedState,
        chainStateRepository: ChainStateRepository
    ) = CrowdloanInteractor(
        accountRepository,
        crowdloanRepository,
        sharedState,
        chainStateRepository
    )

    @Provides
    @FeatureScope
    fun provideCrowdloanMetadataApi(networkApiCreator: NetworkApiCreator): ParachainMetadataApi {
        return networkApiCreator.create(ParachainMetadataApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideCrowdloanContributeInteractor(
        extrinsicService: ExtrinsicService,
        accountRepository: AccountRepository,
        chainStateRepository: ChainStateRepository,
        sharedState: CrowdloanSharedState,
        crowdloanRepository: CrowdloanRepository
    ) = CrowdloanContributeInteractor(
        extrinsicService,
        accountRepository,
        chainStateRepository,
        sharedState,
        crowdloanRepository
    )
}
