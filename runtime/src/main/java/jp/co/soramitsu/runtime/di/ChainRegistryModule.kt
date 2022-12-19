package jp.co.soramitsu.runtime.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFactory
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow

@InstallIn(SingletonComponent::class)
@Module
class ChainRegistryModule {

    @Provides
    @Singleton
    fun provideChainFetcher(apiCreator: NetworkApiCreator) = apiCreator.create(ChainFetcher::class.java)

    @Provides
    @Singleton
    fun provideChainSyncService(
        dao: ChainDao,
        chainFetcher: ChainFetcher
    ) = ChainSyncService(dao, chainFetcher)

    @Provides
    @Singleton
    fun provideRuntimeFactory(
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
        gson: Gson
    ): RuntimeFactory {
        return RuntimeFactory(runtimeFilesCache, chainDao, gson)
    }

    @Provides
    @Singleton
    fun provideRuntimeFilesCache(
        fileProvider: FileProvider
    ) = RuntimeFilesCache(fileProvider)

    @Provides
    @Singleton
    fun provideTypesFetcher(
        networkApiCreator: NetworkApiCreator
    ) = networkApiCreator.create(TypesFetcher::class.java)

    @Provides
    @Singleton
    fun provideRuntimeSyncService(
        typesFetcher: TypesFetcher,
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
        updatesMixin: UpdatesMixin
    ) = RuntimeSyncService(
        typesFetcher = typesFetcher,
        runtimeFilesCache = runtimeFilesCache,
        chainDao = chainDao,
        updatesMixin = updatesMixin
    )

    @Provides
    @Singleton
    fun provideRuntimeProviderPool(
        runtimeFactory: RuntimeFactory,
        runtimeSyncService: RuntimeSyncService
    ) = RuntimeProviderPool(runtimeFactory, runtimeSyncService)

    @Provides
    @Singleton
    fun provideNodeSettingsStorage(preferences: Preferences) = NodesSettingsStorage(preferences)

    @Provides
    @Singleton
    fun provideConnectionPool(
        socketProvider: Provider<SocketService>,
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
        nodesSettingsStorage: NodesSettingsStorage,
        networkStateMixin: NetworkStateMixin
    ) = ConnectionPool(socketProvider, externalRequirementsFlow, nodesSettingsStorage, networkStateMixin)

    @Provides
    @Singleton
    fun provideRuntimeVersionSubscriptionPool(
        chainDao: ChainDao,
        runtimeSyncService: RuntimeSyncService
    ) = RuntimeSubscriptionPool(chainDao, runtimeSyncService)

    @Provides
    @Singleton
    fun provideExternalRequirementsFlow() = MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)

    @Provides
    @Singleton
    fun provideChainRegistry(
        runtimeProviderPool: RuntimeProviderPool,
        chainConnectionPool: ConnectionPool,
        runtimeSubscriptionPool: RuntimeSubscriptionPool,
        chainDao: ChainDao,
        chainSyncService: ChainSyncService,
        runtimeSyncService: RuntimeSyncService,
        updatesMixin: UpdatesMixin
    ) = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        runtimeSyncService,
        updatesMixin
    )
}
