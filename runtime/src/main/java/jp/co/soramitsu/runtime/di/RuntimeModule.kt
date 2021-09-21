package jp.co.soramitsu.runtime.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.data.extrinsic.FeeEstimator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.RuntimeUpdater
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
    fun provideRuntimeUpdater(
        accountRepository: AccountRepository,
        connectionProperty: SuspendableProperty<SocketService>,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        chainRegistry: ChainRegistry,
    ) = RuntimeUpdater(
        accountRepository,
        runtimeProperty,
        connectionProperty,
        chainRegistry
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
        mortalityConstructor: MortalityConstructor,
    ) = ExtrinsicBuilderFactory(
        accountRepository,
        rpcCalls,
        runtimeProperty,
        mortalityConstructor
    )

    @Provides
    @ApplicationScope
    fun provideStorageCache(
        storageDao: StorageDao,
    ): StorageCache = DbStorageCache(storageDao)

    @Provides
    @ApplicationScope
    fun provideFeeEstimator(
        rpcCalls: RpcCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    ): jp.co.soramitsu.feature_account_api.data.extrinsic.FeeEstimator = jp.co.soramitsu.feature_account_api.data.extrinsic.FeeEstimator(rpcCalls, extrinsicBuilderFactory)

    @Provides
    @ApplicationScope
    fun provideExtrinsicService(
        rpcCalls: RpcCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
    ): jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService = jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService(rpcCalls, extrinsicBuilderFactory)

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
        connectionProperty: SuspendableProperty<SocketService>,
        bulkRetriever: BulkRetriever,
    ): StorageDataSource = RemoteStorageSource(runtimeProperty, connectionProperty, bulkRetriever)

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
}
