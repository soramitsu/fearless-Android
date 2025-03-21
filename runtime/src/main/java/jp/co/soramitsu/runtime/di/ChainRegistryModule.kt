package jp.co.soramitsu.runtime.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsSyncServiceProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton

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
        chainFetcher: ChainFetcher,
        metaAccountDao: MetaAccountDao,
        assetDao: AssetDao,

        contextManager: ContextManager
    ) = ChainSyncService(dao, chainFetcher, metaAccountDao, assetDao, contextManager)

    @Provides
    @Singleton
    fun provideRemoteAssetsInitializer(
        dao: ChainDao,
        remoteAssetsSyncServiceProvider: RemoteAssetsSyncServiceProvider
    ): RemoteAssetsInitializer {
        return RemoteAssetsInitializer(dao, remoteAssetsSyncServiceProvider)
    }

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
        connectionPool: ConnectionPool
    ) = RuntimeSyncService(
        typesFetcher = typesFetcher,
        runtimeFilesCache = runtimeFilesCache,
        chainDao = chainDao,
        connectionPool = connectionPool
    )

    @Provides
    @Singleton
    fun provideRuntimeProviderPool(
        runtimeFactory: RuntimeFactory,
        runtimeSyncService: RuntimeSyncService,
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
        networkStateService: NetworkStateService
    ) = RuntimeProviderPool(
        runtimeFactory,
        runtimeSyncService,
        runtimeFilesCache,
        chainDao,
        networkStateService
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
        networkStateService: NetworkStateService
    ) = ConnectionPool(
        socketProvider,
        externalRequirementsFlow,
        nodesSettingsStorage,
        networkStateService
    )

    @Provides
    @Singleton
    fun provideRuntimeVersionSubscriptionPool(
        chainDao: ChainDao,
        runtimeSyncService: RuntimeSyncService,
        networkStateService: NetworkStateService
    ) = RuntimeSubscriptionPool(chainDao, runtimeSyncService, networkStateService)

    @Provides
    @Singleton
    fun provideExternalRequirementsFlow() =
        MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)

    @Provides
    @Singleton
    fun provideEthereumPool(
        networkStateService: NetworkStateService
    ) =
        EthereumConnectionPool(networkStateService)


    @Provides
    @Singleton
    fun provideChainRegistry(
        runtimeProviderPool: RuntimeProviderPool,
        chainConnectionPool: ConnectionPool,
        runtimeSubscriptionPool: RuntimeSubscriptionPool,
        chainDao: ChainDao,
        chainSyncService: ChainSyncService,
        runtimeSyncService: RuntimeSyncService,
        networkStateService: NetworkStateService,
        ethereumConnectionPool: EthereumConnectionPool,
        assetReadOnlyCache: AssetDao,
        chainsRepository: ChainsRepository,
        chainEnvironmentConfiguratorProvider: ChainEnvironmentConfiguratorProvider
    ): ChainRegistry = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        runtimeSyncService,
        networkStateService,
        ethereumConnectionPool,
        assetReadOnlyCache,
        chainsRepository,
        chainEnvironmentConfiguratorProvider
    )

    @Provides
    @Singleton
    fun provideChainEnvironmentConfiguratorProvider(
        connectionPool: ConnectionPool,
        runtimeProviderPool: RuntimeProviderPool,
        runtimeSyncService: RuntimeSyncService,
        runtimeSubscriptionPool: RuntimeSubscriptionPool,
        chainsRepository: ChainsRepository,
        ethereumConnectionPool: EthereumConnectionPool
    ): ChainEnvironmentConfiguratorProvider {
        return ChainEnvironmentConfiguratorProvider(
            connectionPool,
            runtimeProviderPool,
            runtimeSyncService,
            runtimeSubscriptionPool,
            chainsRepository,
            ethereumConnectionPool
        )
    }

    @Provides
    fun provideChainsRepository(chainDao: ChainDao): ChainsRepository = ChainsRepository(chainDao)
}
