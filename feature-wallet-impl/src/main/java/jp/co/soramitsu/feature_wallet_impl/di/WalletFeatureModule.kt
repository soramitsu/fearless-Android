package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.di.Wallet
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.MoonPayProvider
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.RampProvider
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.BalancesUpdateSystem
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.PaymentUpdaterFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.RuntimeWalletConstants
import jp.co.soramitsu.feature_wallet_impl.data.repository.TokenRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.BuyMixinProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecksProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

@Module
class WalletFeatureModule {

    @Provides
    @FeatureScope
    fun provideSubQueryApi(networkApiCreator: NetworkApiCreator): SubQueryOperationsApi {
        return networkApiCreator.create(SubQueryOperationsApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideCoingeckoApi(networkApiCreator: NetworkApiCreator): CoingeckoApi {
        return networkApiCreator.create(CoingeckoApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideAssetCache(
        tokenDao: TokenDao,
        assetDao: AssetDao,
        accountRepository: AccountRepository
    ): AssetCache {
        return AssetCache(tokenDao, accountRepository, assetDao)
    }

    @Provides
    @FeatureScope
    fun providePhishingApi(networkApiCreator: NetworkApiCreator): PhishingApi {
        return networkApiCreator.create(PhishingApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideHistoryFiltersProvider() = HistoryFiltersProvider()

    @Provides
    @FeatureScope
    fun provideSubstrateSource(
        rpcCalls: RpcCalls,
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        extrinsicService: ExtrinsicService,
    ): SubstrateRemoteSource = WssSubstrateSource(
        rpcCalls,
        remoteStorageSource,
        extrinsicService
    )

    @Provides
    @FeatureScope
    fun provideTokenRepository(
        tokenDao: TokenDao,
    ): TokenRepository = TokenRepositoryImpl(
        tokenDao
    )

    @Provides
    @FeatureScope
    fun provideCursorStorage(preferences: Preferences) = TransferCursorStorage(preferences)

    @Provides
    @FeatureScope
    fun provideWalletRepository(
        substrateSource: SubstrateRemoteSource,
        operationsDao: OperationDao,
        subQueryOperationsApi: SubQueryOperationsApi,
        httpExceptionHandler: HttpExceptionHandler,
        phishingApi: PhishingApi,
        phishingAddressDao: PhishingAddressDao,
        walletConstants: WalletConstants,
        assetCache: AssetCache,
        coingeckoApi: CoingeckoApi,
        cursorStorage: TransferCursorStorage,
        chainRegistry: ChainRegistry,
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        operationsDao,
        subQueryOperationsApi,
        httpExceptionHandler,
        phishingApi,
        assetCache,
        walletConstants,
        phishingAddressDao,
        cursorStorage,
        coingeckoApi,
        chainRegistry
    )

    @Provides
    @FeatureScope
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        fileProvider: FileProvider,
    ): WalletInteractor = WalletInteractorImpl(
        walletRepository,
        accountRepository,
        chainRegistry,
        fileProvider
    )

    @Provides
    @FeatureScope
    fun provideBuyTokenIntegration(): BuyTokenRegistry {
        return BuyTokenRegistry(
            availableProviders = listOf(
                RampProvider(host = BuildConfig.RAMP_HOST, apiToken = BuildConfig.RAMP_TOKEN),
                MoonPayProvider(host = BuildConfig.MOONPAY_HOST, publicKey = BuildConfig.MOONPAY_PUBLIC_KEY, privateKey = BuildConfig.MOONPAY_PRIVATE_KEY)
            )
        )
    }

    @Provides
    fun provideBuyMixin(
        buyTokenRegistry: BuyTokenRegistry,
    ): BuyMixin.Presentation = BuyMixinProvider(buyTokenRegistry)

    @Provides
    @FeatureScope
    fun provideTransferChecks(): TransferValidityChecks.Presentation = TransferValidityChecksProvider()

    @Provides
    @FeatureScope
    fun providePaymentUpdaterFactory(
        remoteSource: SubstrateRemoteSource,
        assetCache: AssetCache,
        operationDao: OperationDao,
        accountUpdateScope: AccountUpdateScope,
        chainRegistry: ChainRegistry,
    ) = PaymentUpdaterFactory(
        remoteSource,
        assetCache,
        operationDao,
        chainRegistry,
        accountUpdateScope
    )

    @Provides
    @Wallet
    @FeatureScope
    fun provideFeatureUpdaters(
        chainRegistry: ChainRegistry,
        paymentUpdaterFactory: PaymentUpdaterFactory,
    ): UpdateSystem = BalancesUpdateSystem(
        chainRegistry,
        paymentUpdaterFactory,
    )

    @Provides
    @FeatureScope
    fun provideWalletConstants(
        chainRegistry: ChainRegistry,
    ): WalletConstants = RuntimeWalletConstants(chainRegistry)
}
