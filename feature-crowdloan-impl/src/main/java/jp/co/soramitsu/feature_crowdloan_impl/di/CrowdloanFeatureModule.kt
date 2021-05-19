package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.ParachainMetadataApi
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

@Module
class CrowdloanFeatureModule {

    @Provides
    @FeatureScope
    fun crowdloanRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        crowdloanMetadataApi: ParachainMetadataApi,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        accountRepository: AccountRepository,
    ): CrowdloanRepository = CrowdloanRepositoryImpl(
        remoteStorageSource,
        accountRepository,
        runtimeProperty,
        crowdloanMetadataApi
    )

    @Provides
    @FeatureScope
    fun provideCrowdloanInteractor(repository: CrowdloanRepository) = CrowdloanInteractor(repository)

    @Provides
    @FeatureScope
    fun provideCrowdloanMetadataApi(networkApiCreator: NetworkApiCreator): ParachainMetadataApi {
        return networkApiCreator.create(ParachainMetadataApi::class.java)
    }
}
