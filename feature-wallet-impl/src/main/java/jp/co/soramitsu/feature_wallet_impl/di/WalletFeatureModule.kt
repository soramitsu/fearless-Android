package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.RampProvider
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.PaymentUpdater
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.TokenRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixinProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecksProvider
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory

@Module
class WalletFeatureModule {

    @Provides
    @FeatureScope
    fun provideSubscanApi(networkApiCreator: NetworkApiCreator): SubscanNetworkApi {
        return networkApiCreator.create(SubscanNetworkApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideAssetCache(tokenDao: TokenDao, assetDao: AssetDao): AssetCache {
        return AssetCache(tokenDao, assetDao)
    }

    @Provides
    @FeatureScope
    fun provideDualRefCountProperty() = SuspendableProperty<Boolean>()

    @Provides
    @FeatureScope
    fun providePhishingApi(networkApiCreator: NetworkApiCreator): PhishingApi {
        return networkApiCreator.create(PhishingApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideSubstrateSource(
        socketService: SocketService,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    ): SubstrateRemoteSource = WssSubstrateSource(
        socketService,
        runtimeProperty,
        extrinsicBuilderFactory,
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
    fun provideWalletRepository(
        substrateSource: SubstrateRemoteSource,
        transactionDao: TransactionDao,
        subscanNetworkApi: SubscanNetworkApi,
        httpExceptionHandler: HttpExceptionHandler,
        phishingApi: PhishingApi,
        phishingAddressDao: PhishingAddressDao,
        assetCache: AssetCache,
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        transactionDao,
        subscanNetworkApi,
        httpExceptionHandler,
        phishingApi,
        assetCache,
        phishingAddressDao
    )

    @Provides
    @FeatureScope
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        fileProvider: FileProvider,
    ): WalletInteractor = WalletInteractorImpl(walletRepository, accountRepository, fileProvider)

    @Provides
    @FeatureScope
    fun provideBuyTokenIntegration(): BuyTokenRegistry {
        return BuyTokenRegistry(
            availableProviders = listOf(
                RampProvider(host = BuildConfig.RAMP_HOST, apiToken = BuildConfig.RAMP_TOKEN)
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
    fun providePaymentUpdater(
        remoteSource: SubstrateRemoteSource,
        assetCache: AssetCache,
        transactionDao: TransactionDao,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        accountUpdateScope: AccountUpdateScope,
    ): PaymentUpdater {
        return PaymentUpdater(
            remoteSource,
            assetCache,
            transactionDao,
            runtimeProperty,
            accountUpdateScope
        )
    }

    @Provides
    @FeatureScope
    fun provideFeatureUpdaters(
        paymentUpdater: PaymentUpdater,
    ): WalletUpdaters = WalletUpdaters(
        updaters = arrayOf(paymentUpdater)
    )
}
