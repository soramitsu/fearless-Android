package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
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
    ): CrowdloanRepository = CrowdloanRepositoryImpl(remoteStorageSource)

    @Provides
    @FeatureScope
    fun provideCrowdloanInteractor(repository: CrowdloanRepository) = CrowdloanInteractor(repository)
}
