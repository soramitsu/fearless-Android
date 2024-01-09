package jp.co.soramitsu.runtime.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.coredb.dao.StorageDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.DbStorageCache
import jp.co.soramitsu.runtime.storage.source.LocalStorageSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named
import javax.inject.Singleton

const val LOCAL_STORAGE_SOURCE = "LOCAL_STORAGE_SOURCE"
const val REMOTE_STORAGE_SOURCE = "REMOTE_STORAGE_SOURCE"

@InstallIn(SingletonComponent::class)
@Module
class RuntimeModule {

    @Provides
    @Singleton
    fun provideExtrinsicBuilderFactory(
        rpcCalls: RpcCalls,
        chainRegistry: ChainRegistry,
        mortalityConstructor: MortalityConstructor
    ) = ExtrinsicBuilderFactory(
        rpcCalls,
        chainRegistry,
        mortalityConstructor
    )

    @Provides
    @Singleton
    fun provideStorageCache(
        storageDao: StorageDao
    ): StorageCache = DbStorageCache(storageDao)

    @Provides
    @Named(LOCAL_STORAGE_SOURCE)
    @Singleton
    fun provideLocalStorageSource(
        chainRegistry: ChainRegistry,
        storageCache: StorageCache
    ): StorageDataSource = LocalStorageSource(chainRegistry, storageCache)

    @Provides
    @Named(REMOTE_STORAGE_SOURCE)
    @Singleton
    fun provideRemoteStorageSource(
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever
    ): StorageDataSource = RemoteStorageSource(chainRegistry, bulkRetriever)

    @Provides
    @Singleton
    fun provideChainStateRepository(
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        chainRegistry: ChainRegistry
    ): IChainStateRepository = ChainStateRepository(localStorageSource, chainRegistry)

    @Provides
    @Singleton
    fun provideMortalityProvider(
        chainRegistry: ChainRegistry,
        rpcCalls: RpcCalls
    ) = MortalityConstructor(rpcCalls, chainRegistry)

    @Provides
    @Singleton
    fun provideSubstrateCalls(
        chainRegistry: ChainRegistry
    ) = RpcCalls(chainRegistry)
}
