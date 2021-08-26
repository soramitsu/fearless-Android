package jp.co.soramitsu.runtime.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core_db.dao.ChainDao
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
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Provider

@Module
class ChainRegistryModule {

    @Provides
    @ApplicationScope
    fun provideChainFetcher(apiCreator: NetworkApiCreator) = apiCreator.create(ChainFetcher::class.java)

    @Provides
    @ApplicationScope
    fun provideChainSyncService(
        dao: ChainDao,
        chainFetcher: ChainFetcher,
    ) = ChainSyncService(dao, chainFetcher)

    @Provides
    @ApplicationScope
    fun provideRuntimeFactory(
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
        gson: Gson,
    ): RuntimeFactory {
        return RuntimeFactory(runtimeFilesCache, chainDao, gson)
    }

    @Provides
    @ApplicationScope
    fun provideRuntimeFilesCache(
        fileProvider: FileProvider,
    ) = RuntimeFilesCache(fileProvider)

    @Provides
    @ApplicationScope
    fun provideTypesFetcher(
        networkApiCreator: NetworkApiCreator,
    ) = networkApiCreator.create(TypesFetcher::class.java)

    @Provides
    @ApplicationScope
    fun provideRuntimeSyncService(
        typesFetcher: TypesFetcher,
        runtimeFilesCache: RuntimeFilesCache,
        chainDao: ChainDao,
    ) = RuntimeSyncService(typesFetcher, runtimeFilesCache, chainDao)

    @Provides
    @ApplicationScope
    fun provideBaseTypeSynchronizer(
        typesFetcher: TypesFetcher,
        runtimeFilesCache: RuntimeFilesCache,
    ) = BaseTypeSynchronizer(runtimeFilesCache, typesFetcher)

    @Provides
    @ApplicationScope
    fun provideRuntimeProviderPool(
        runtimeFactory: RuntimeFactory,
        runtimeSyncService: RuntimeSyncService,
        baseTypeSynchronizer: BaseTypeSynchronizer,
    ) = RuntimeProviderPool(runtimeFactory, runtimeSyncService, baseTypeSynchronizer)

    @Provides
    @ApplicationScope
    fun provideConnectionPool(
        socketProvider: Provider<SocketService>,
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>
    ) = ConnectionPool(socketProvider, externalRequirementsFlow)

    @Provides
    @ApplicationScope
    fun provideRuntimeVersionSubscriptionPool(
        chainDao: ChainDao,
        runtimeSyncService: RuntimeSyncService,
    ) = RuntimeSubscriptionPool(chainDao, runtimeSyncService)

    @Provides
    @ApplicationScope
    fun provideExternalRequirementsFlow() = MutableStateFlow(ChainConnection.ExternalRequirement.FORBIDDEN)

    @Provides
    @ApplicationScope
    fun provideChainRegistry(
        runtimeProviderPool: RuntimeProviderPool,
        chainConnectionPool: ConnectionPool,
        runtimeSubscriptionPool: RuntimeSubscriptionPool,
        chainDao: ChainDao,
        chainSyncService: ChainSyncService,
        baseTypeSynchronizer: BaseTypeSynchronizer,
        runtimeSyncService: RuntimeSyncService,
    ) = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        baseTypeSynchronizer,
        runtimeSyncService
    )
}
