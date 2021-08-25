package jp.co.soramitsu.runtime.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.calls.RpcCalls
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.DefinitionsFetcher
import jp.co.soramitsu.runtime.RuntimeCache
import jp.co.soramitsu.runtime.RuntimeConstructor
import jp.co.soramitsu.runtime.RuntimePrepopulator
import jp.co.soramitsu.runtime.RuntimeUpdater
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.extrinsic.MortalityConstructor
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.NetworkAwareStorageCache
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
    fun provideDefinitionsFetcher(
        apiCreator: NetworkApiCreator,
    ) = apiCreator.create(DefinitionsFetcher::class.java)

    @Provides
    @ApplicationScope
    fun provideRuntimeCache(
        fileProvider: FileProvider,
    ) = RuntimeCache(fileProvider)

    @Provides
    @ApplicationScope
    fun provideRuntimePrepopulator(
        context: Context,
        runtimeDao: RuntimeDao,
        preferences: Preferences,
        runtimeCache: RuntimeCache,
    ) = RuntimePrepopulator(
        context,
        runtimeDao,
        preferences,
        runtimeCache
    )

    @Provides
    @ApplicationScope
    fun provideRuntimeConstructor(
        socketService: SocketService,
        gson: Gson,
        definitionsFetcher: DefinitionsFetcher,
        runtimeDao: RuntimeDao,
        runtimeCache: RuntimeCache,
    ) = RuntimeConstructor(
        socketService,
        definitionsFetcher,
        gson,
        runtimeDao,
        runtimeCache
    )

    @Provides
    @ApplicationScope
    fun provideRuntimeUpdater(
        accountRepository: AccountRepository,
        socketService: SocketService,
        runtimeConstructor: RuntimeConstructor,
        runtimePrepopulator: RuntimePrepopulator,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    ) = RuntimeUpdater(
        runtimeConstructor,
        socketService,
        accountRepository,
        runtimePrepopulator,
        runtimeProperty
    )

    @Provides
    @ApplicationScope
    fun provideRuntimeProperty() = SuspendableProperty<RuntimeSnapshot>()

    @Provides
    @ApplicationScope
    fun provideExtrinsicBuilderFactory(
        accountRepository: AccountRepository,
        rpcCalls: RpcCalls,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        keypairFactory: KeypairFactory,
        mortalityConstructor: MortalityConstructor,
    ) = ExtrinsicBuilderFactory(
        accountRepository,
        rpcCalls,
        keypairFactory,
        runtimeProperty,
        mortalityConstructor
    )

    @Provides
    @ApplicationScope
    fun provideStorageCache(
        storageDao: StorageDao,
        runtimeDao: RuntimeDao,
        accountRepository: AccountRepository,
    ): StorageCache = NetworkAwareStorageCache(storageDao, runtimeDao, accountRepository)

    @Provides
    @ApplicationScope
    fun provideFeeEstimator(
        rpcCalls: RpcCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    ): FeeEstimator = FeeEstimator(rpcCalls, extrinsicBuilderFactory)

    @Provides
    @ApplicationScope
    fun provideExtrinsicService(
        rpcCalls: RpcCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    ): ExtrinsicService = ExtrinsicService(rpcCalls, extrinsicBuilderFactory)

    @Provides
    @Named(LOCAL_STORAGE_SOURCE)
    @ApplicationScope
    fun provideLocalStorageSource(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache,
    ): StorageDataSource = LocalStorageSource(runtimeProperty, storageCache)

    @Provides
    @Named(REMOTE_STORAGE_SOURCE)
    @ApplicationScope
    fun provideRemoteStorageSource(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        socketService: SocketService,
        bulkRetriever: BulkRetriever,
    ): StorageDataSource = RemoteStorageSource(runtimeProperty, socketService, bulkRetriever)

    @Provides
    @ApplicationScope
    fun provideChainStateRepository(
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    ) = ChainStateRepository(localStorageSource, runtimeProperty)

    @Provides
    @ApplicationScope
    fun provideMortalityProvider(
        chainStateRepository: ChainStateRepository,
        rpcCalls: RpcCalls,
    ) = MortalityConstructor(rpcCalls, chainStateRepository)

    @Provides
    @ApplicationScope
    fun provideChainFetcher(apiCreator: NetworkApiCreator) = apiCreator.create(ChainFetcher::class.java)

    @Provides
    @ApplicationScope
    fun provideChainSyncService(
        dao: ChainDao,
        chainFetcher: ChainFetcher
    ) = ChainSyncService(dao, chainFetcher)
}
