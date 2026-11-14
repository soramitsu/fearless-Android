package jp.co.soramitsu.crowdloan.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanRecovery
import jp.co.soramitsu.crowdloan.impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.crowdloan.impl.domain.LegacyCrowdloanRecoveryImpl
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named
import javax.inject.Singleton

/**
 * Crowdloans can no longer be contributed to, so only the read-only relay state that legacy crowdloan
 * recovery uses to show locked or claimed contributions remains.
 */
@InstallIn(SingletonComponent::class)
@Module
class CrowdloanFeatureModule {

    @Provides
    fun crowdloanRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        chainRegistry: ChainRegistry
    ): CrowdloanRepository = CrowdloanRepositoryImpl(
        remoteStorageSource,
        chainRegistry
    )

    @Provides
    @Singleton
    fun provideLegacyCrowdloanRecovery(
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        crowdloanRepository: CrowdloanRepository,
        operationDao: OperationDao
    ): LegacyCrowdloanRecovery = LegacyCrowdloanRecoveryImpl(
        accountRepository = accountRepository,
        chainRegistry = chainRegistry,
        crowdloanRepository = crowdloanRepository,
        operationDao = operationDao
    )
}
