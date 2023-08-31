package jp.co.soramitsu.runtime.di

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
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketFactory
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

@InstallIn(SingletonComponent::class)
@Module
class ChainRegistryModule {

    @Provides
    @Singleton
    fun provideChainFetcher(apiCreator: NetworkApiCreator) =
        apiCreator.create(ChainFetcher::class.java)

    @Provides
    @Singleton
    fun provideChainSyncService(
        dao: ChainDao,
        chainFetcher: ChainFetcher
    ) = ChainSyncService(dao, chainFetcher)

    @Provides
    @Singleton
    fun provideJson(): Json {
        return JsonFactory.create()
    }

    @Provides
    @Singleton
    fun provideRuntimeFactory(json: Json): RuntimeFactory {
        return RuntimeFactory(json)
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
        updatesMixin: UpdatesMixin,
        connectionPool: ConnectionPool
    ) = RuntimeSyncService(
        typesFetcher = typesFetcher,
        runtimeFilesCache = runtimeFilesCache,
        chainDao = chainDao,
        updatesMixin = updatesMixin,
        connectionPool = connectionPool
    )

    @Provides
    @Singleton
    fun provideRuntimeProviderPool(
        runtimeFactory: RuntimeFactory,
        runtimeSyncService: RuntimeSyncService,
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
        networkStateMixin: NetworkStateMixin
    ) = RuntimeProviderPool(
        runtimeFactory,
        runtimeSyncService,
        runtimeFilesCache,
        chainDao,
        networkStateMixin
    )

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
    ) = ConnectionPool(
        socketProvider,
        externalRequirementsFlow,
        nodesSettingsStorage,
        networkStateMixin
    )

    @Provides
    @Singleton
    fun provideRuntimeVersionSubscriptionPool(
        chainDao: ChainDao,
        runtimeSyncService: RuntimeSyncService
    ) = RuntimeSubscriptionPool(chainDao, runtimeSyncService)

    @Provides
    @Singleton
    fun provideExternalRequirementsFlow() =
        MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)

    @Provides
    fun provideEthereumSocketFactory(): EthereumWebSocketFactory = EthereumWebSocketFactory()

    @Provides
    @Singleton
    fun provideEthereumPool(
        networkStateMixin: NetworkStateMixin,
        ethereumWebSocketFactory: EthereumWebSocketFactory,
        nodesSettingsStorage: NodesSettingsStorage
    ) =
        EthereumConnectionPool(networkStateMixin, ethereumWebSocketFactory, nodesSettingsStorage)

    @Provides
    @Singleton
    fun provideChainRegistry(
        runtimeProviderPool: RuntimeProviderPool,
        chainConnectionPool: ConnectionPool,
        runtimeSubscriptionPool: RuntimeSubscriptionPool,
        chainDao: ChainDao,
        chainSyncService: ChainSyncService,
        runtimeSyncService: RuntimeSyncService,
        updatesMixin: UpdatesMixin,
        networkStateMixin: NetworkStateMixin,
        ethereumConnectionPool: EthereumConnectionPool
    ): ChainRegistry = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        runtimeSyncService,
        updatesMixin,
        networkStateMixin,
        ethereumConnectionPool
    )
}
