package jp.co.soramitsu.app.root.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.data.runtime.DefinitionsFetcher
import jp.co.soramitsu.app.root.data.runtime.RuntimeCache
import jp.co.soramitsu.app.root.data.runtime.RuntimeConstructor
import jp.co.soramitsu.app.root.data.runtime.RuntimePrepopulator
import jp.co.soramitsu.app.root.data.runtime.RuntimeUpdater
import jp.co.soramitsu.app.root.domain.CompositeUpdater
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootUpdater(
        walletUpdaters: WalletUpdaters,
        runtimeUpdater: RuntimeUpdater
    ): Updater {
        return CompositeUpdater(
            *walletUpdaters.updaters,
            runtimeUpdater
        )
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        accountRepository: AccountRepository,
        rootUpdater: Updater,
        buyTokenRegistry: BuyTokenRegistry,
        walletRepository: WalletRepository
    ): RootInteractor {
        return RootInteractor(accountRepository, rootUpdater, buyTokenRegistry, walletRepository)
    }

    @Provides
    @FeatureScope
    fun provideDefinitionsFetcher(
        apiCreator: NetworkApiCreator
    ) = apiCreator.create(DefinitionsFetcher::class.java)

    @Provides
    @FeatureScope
    fun provideRuntimeCache(
        fileProvider: FileProvider
    ) = RuntimeCache(fileProvider)

    @Provides
    @FeatureScope
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
    @FeatureScope
    fun provideRuntimeContructor(
        socketService: SocketService,
        gson: Gson,
        definitionsFetcher: DefinitionsFetcher,
        runtimeDao: RuntimeDao,
        runtimePrepopulator: RuntimePrepopulator,
        runtimeCache: RuntimeCache
    ) = RuntimeConstructor(
        socketService,
        definitionsFetcher,
        gson,
        runtimeDao,
        runtimePrepopulator,
        runtimeCache
    )

    @Provides
    @FeatureScope
    fun provideRuntimeUpdater(
        accountRepository: AccountRepository,
        socketService: SocketService,
        runtimeConstructor: RuntimeConstructor,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = RuntimeUpdater(
        runtimeConstructor,
        socketService,
        accountRepository,
        runtimeProperty
    )

    @Provides
    @FeatureScope
    fun provideRuntimeProperty() = SuspendableProperty<RuntimeSnapshot>()
}