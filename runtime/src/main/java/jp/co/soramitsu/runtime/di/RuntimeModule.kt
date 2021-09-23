package jp.co.soramitsu.runtime.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.MortalityConstructor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.DbStorageCache
import jp.co.soramitsu.runtime.storage.source.LocalStorageSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

const val LOCAL_STORAGE_SOURCE = "LOCAL_STORAGE_SOURCE"
const val REMOTE_STORAGE_SOURCE = "REMOTE_STORAGE_SOURCE"

@Module
class RuntimeModule {

    @Provides
    @ApplicationScope
    fun provideExtrinsicBuilderFactory(
        rpcCalls: RpcCalls,
        chainRegistry: ChainRegistry,
        mortalityConstructor: MortalityConstructor,
    ) = ExtrinsicBuilderFactory(
        rpcCalls,
        chainRegistry,
        mortalityConstructor
    )

    @Provides
    @ApplicationScope
    fun provideStorageCache(
        storageDao: StorageDao,
    ): StorageCache = DbStorageCache(storageDao)


    @Provides
    @Named(LOCAL_STORAGE_SOURCE)
    @ApplicationScope
    fun provideLocalStorageSource(
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ): StorageDataSource = LocalStorageSource(chainRegistry, storageCache)

    @Provides
    @Named(REMOTE_STORAGE_SOURCE)
    @ApplicationScope
    fun provideRemoteStorageSource(
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
    ): StorageDataSource = RemoteStorageSource(chainRegistry, bulkRetriever)

    @Provides
    @ApplicationScope
    fun provideChainStateRepository(
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        chainRegistry: ChainRegistry
    ) = ChainStateRepository(localStorageSource, chainRegistry)

    @Provides
    @ApplicationScope
    fun provideMortalityProvider(
        chainStateRepository: ChainStateRepository,
        rpcCalls: RpcCalls,
    ) = MortalityConstructor(rpcCalls, chainStateRepository)

    @Provides
    @ApplicationScope
    fun provideSubstrateCalls(
        chainRegistry: ChainRegistry
    ) = RpcCalls(chainRegistry)
}
