package jp.co.soramitsu.polkaswap.impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.polkaswap.api.sorablockexplorer.BlockExplorerManager
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigProvider

@InstallIn(SingletonComponent::class)
@Module
class PolkaswapFeatureModule {

    @Provides
    fun providePolkaswapRepositoryImpl(
        remoteConfigFetcher: RemoteConfigFetcher,
        @Named(REMOTE_STORAGE_SOURCE) remoteSource: StorageDataSource,
        extrinsicService: ExtrinsicService,
        chainRegistry: ChainRegistry,
        rpcCalls: RpcCalls,
        accountRepository: AccountRepository,
        walletRepository: WalletRepository,
        sorablockexplorer: BlockExplorerManager,
        poolDao: PoolDao,
        appDataBase: AppDatabase
    ): PolkaswapRepository {
        return PolkaswapRepositoryImpl(
            remoteConfigFetcher,
            remoteSource,
            extrinsicService,
            chainRegistry,
            rpcCalls,
            accountRepository,
            walletRepository,
            sorablockexplorer,
            poolDao,
            appDataBase
        )
    }

    @Provides
    @Singleton
    fun providePolkaswapInteractor(
        chainRegistry: ChainRegistry,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        polkaswapRepository: PolkaswapRepository,
        sharedPreferences: Preferences,
        chainsRepository: ChainsRepository,
    ): PolkaswapInteractor {
        return PolkaswapInteractorImpl(
            chainRegistry,
            walletRepository,
            accountRepository,
            polkaswapRepository,
            sharedPreferences,
            chainsRepository
        )
    }

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
        networkStateService: NetworkStateService,
        ethereumConnectionPool: EthereumConnectionPool,
        assetReadOnlyCache: AssetDao,
        chainsRepository: ChainsRepository,
    ): IChainRegistry = ChainRegistry(
        runtimeProviderPool,
        chainConnectionPool,
        runtimeSubscriptionPool,
        chainDao,
        chainSyncService,
        runtimeSyncService,
        updatesMixin,
        networkStateService,
        ethereumConnectionPool,
        assetReadOnlyCache,
        chainsRepository
    )

    @Singleton
    @Provides
    fun provideSoraWalletBlockExplorerInfo(
        client: SoramitsuNetworkClient,
        soraRemoteConfigBuilder: SoraRemoteConfigBuilder,
    ): SoraWalletBlockExplorerInfo {
        return SoraWalletBlockExplorerInfo(
            networkClient = client,
            soraRemoteConfigBuilder = soraRemoteConfigBuilder,
        )
    }

//    @Singleton
//    @Provides
//    fun provideSoramitsuNetworkClient(): SoramitsuNetworkClient =
//        SoramitsuNetworkClient(logging = BuildConfig.DEBUG, timeout = 20000)
//
    @Singleton
    @Provides
    fun provideSoraRemoteConfigBuilder(
        client: SoramitsuNetworkClient,
        @ApplicationContext context: Context,
    ): SoraRemoteConfigBuilder {
        return SoraRemoteConfigProvider(
            context = context,
            client = client,
            commonUrl = OptionsProvider.soraConfigCommon,
            mobileUrl = OptionsProvider.soraConfigMobile,
        ).provide()
    }

}
