package jp.co.soramitsu.runtime.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.subquery.subQueryTransactionPageSize
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.xnetworking.fearless.FearlessChainsBuilder
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.subquery.SubQueryClient
import jp.co.soramitsu.xnetworking.subquery.factory.SubQueryClientForFearless
import jp.co.soramitsu.xnetworking.subquery.history.SubQueryHistoryItem
import jp.co.soramitsu.xnetworking.subquery.history.fearless.FearlessSubQueryResponse
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.blockexplorer.SubQueryHistoryHandler
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
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Provider

@Module
class ChainRegistryModule {

    @Provides
    @ApplicationScope
    fun provideChainFetcher(client: SoramitsuNetworkClient): ChainFetcher = ChainFetcher(client)

    @Provides
    @ApplicationScope
    fun provideSoraNetworkClient(): SoramitsuNetworkClient = SoramitsuNetworkClient(logging = BuildConfig.DEBUG)

    @Provides
    @ApplicationScope
    fun provideFearlessChainBuilder(client: SoramitsuNetworkClient): FearlessChainsBuilder = FearlessChainsBuilder(client, BuildConfig.CHAINS_URL)

    @Provides
    @ApplicationScope
    fun provideSubQueryClient(
        context: Context,
        client: SoramitsuNetworkClient,
    ): SubQueryClient<FearlessSubQueryResponse, SubQueryHistoryItem> =
        SubQueryClientForFearless.build(
            context, client, "", subQueryTransactionPageSize
        )

    @Provides
    @ApplicationScope
    fun provideSubQueryHistoryHandler(
        client: SubQueryClient<FearlessSubQueryResponse, SubQueryHistoryItem>,
    ): SubQueryHistoryHandler = SubQueryHistoryHandler(client)

    @Provides
    @ApplicationScope
    fun provideChainSyncService(
        dao: ChainDao,
        chainFetcher: ChainFetcher,
        chainBuilder: FearlessChainsBuilder,
        gson: Gson,
    ) = ChainSyncService(dao, chainFetcher, chainBuilder, gson)

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
        networkClient: SoramitsuNetworkClient,
    ) = TypesFetcher(networkClient)

    @Provides
    @ApplicationScope
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
    fun provideNodeSettingsStorage(preferences: Preferences) = NodesSettingsStorage(preferences)

    @Provides
    @ApplicationScope
    fun provideConnectionPool(
        socketProvider: Provider<SocketService>,
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
        nodesSettingsStorage: NodesSettingsStorage,
        networkStateMixin: NetworkStateMixin
    ) = ConnectionPool(socketProvider, externalRequirementsFlow, nodesSettingsStorage, networkStateMixin)

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
        updatesMixin: UpdatesMixin
    ) = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        baseTypeSynchronizer,
        runtimeSyncService,
        updatesMixin
    )
}
