package jp.co.soramitsu.runtime.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.StorageDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsSyncServiceProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
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
    @Singleton
    fun provideRemoteStorageSource(
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever
    ): RemoteStorageSource = RemoteStorageSource(chainRegistry, bulkRetriever)

    @Provides
    @Named(REMOTE_STORAGE_SOURCE)
    @Singleton
    fun provideRemoteStorageDataSource(
        remoteStorageSource: RemoteStorageSource
    ): StorageDataSource = remoteStorageSource

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

    @Provides
    @Singleton
    fun provideTonRemoteSource(tonApi: TonApi, availableFiatCurrencies: GetAvailableFiatCurrencies, gson: Gson) = TonRemoteSource(tonApi, availableFiatCurrencies, gson)

    @Provides
    @Singleton
    fun provideTonSyncDataRepository(tonRemoteSource: TonRemoteSource) = TonSyncDataRepository(tonRemoteSource)

    @Provides
    @Singleton
    fun provideRemoteAssetsSyncServiceProvider(
        //okxApiService: OkxApiService,
        tonSyncDataRepository: TonSyncDataRepository,
        metaAccountDao: MetaAccountDao,
        chainDao: ChainDao
    ): RemoteAssetsSyncServiceProvider {
        return RemoteAssetsSyncServiceProvider(/* okxApiService, */tonSyncDataRepository, metaAccountDao, chainDao)
    }
}
