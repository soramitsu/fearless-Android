package jp.co.soramitsu.app.root.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.data.runtime.DefinitionsFetcher
import jp.co.soramitsu.app.root.data.runtime.RuntimeCache
import jp.co.soramitsu.app.root.data.runtime.RuntimeHolder
import jp.co.soramitsu.app.root.data.runtime.RuntimeProvider
import jp.co.soramitsu.app.root.data.runtime.RuntimeUpdater
import jp.co.soramitsu.app.root.domain.CompositeUpdater
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootUpdater(
        walletUpdaters: WalletUpdaters
    ): Updater {
        return CompositeUpdater(walletUpdaters.updaters)
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        accountRepository: AccountRepository,
        rootUpdater: Updater,
        buyTokenRegistry: BuyTokenRegistry
    ): RootInteractor {
        return RootInteractor(accountRepository, rootUpdater, buyTokenRegistry)
    }

    @Provides
    @FeatureScope
    fun provideRuntimeCache(
        preferences: Preferences,
        fileProvider: FileProvider
    ) = RuntimeCache(preferences, fileProvider)

    @Provides
    @FeatureScope
    fun provideDefinitionsFetcher(
        apiCreator: NetworkApiCreator
    ) = apiCreator.create(DefinitionsFetcher::class.java)

    @Provides
    @FeatureScope
    fun provideRuntimeProvider(
        socketService: SocketService,
        gson: Gson,
        definitionsFetcher: DefinitionsFetcher,
        runtimeDao: RuntimeDao,
        runtimeCache: RuntimeCache
    ) = RuntimeProvider(
        socketService,
        definitionsFetcher,
        gson,
        runtimeDao,
        runtimeCache
    )

    @Provides
    @FeatureScope
    fun provideRuntimeUpdater(
        socketService: SocketService,
        runtimeProvider: RuntimeProvider,
        runtimeHolder: RuntimeHolder
    ) = RuntimeUpdater(
        runtimeProvider,
        socketService,
        runtimeHolder
    )

    @Provides
    @FeatureScope
    fun provideRuntimeHolder() = RuntimeHolder()
}