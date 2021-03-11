package jp.co.soramitsu.runtime.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
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
import jp.co.soramitsu.runtime.storage.NetworkAwareStorageCache

@Module
class RuntimeModule {

    @Provides
    @ApplicationScope
    fun provideDefinitionsFetcher(
        apiCreator: NetworkApiCreator
    ) = apiCreator.create(DefinitionsFetcher::class.java)

    @Provides
    @ApplicationScope
    fun provideRuntimeCache(
        fileProvider: FileProvider
    ) = RuntimeCache(fileProvider)

    @Provides
    @ApplicationScope
    fun provideRuntimePrepopulator(
        context: Context,
        runtimeDao: RuntimeDao,
        preferences: Preferences,
        runtimeCache: RuntimeCache
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
        runtimeCache: RuntimeCache
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
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
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
        substrateCalls: SubstrateCalls,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        keypairFactory: KeypairFactory
    ) = ExtrinsicBuilderFactory(
        accountRepository,
        substrateCalls,
        keypairFactory,
        runtimeProperty
    )

    @Provides
    @ApplicationScope
    fun provideStorageCache(
        storageDao: StorageDao,
        runtimeDao: RuntimeDao,
        accountRepository: AccountRepository
    ): StorageCache = NetworkAwareStorageCache(storageDao, runtimeDao, accountRepository)
}