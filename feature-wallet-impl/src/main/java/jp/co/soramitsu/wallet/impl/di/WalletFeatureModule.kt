package jp.co.soramitsu.wallet.impl.di

import android.content.ContentResolver
import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrBitmapDecoder
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.coredb.dao.AddressBookDao
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecksProvider
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.MoonPayProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.RampProvider
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySourceProvider
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalancesUpdateSystem
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.PaymentUpdaterFactory
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.data.repository.AddressBookRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.repository.HistoryRepository
import jp.co.soramitsu.wallet.impl.data.repository.RuntimeWalletConstants
import jp.co.soramitsu.wallet.impl.data.repository.TokenRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.ValidateTransferUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.WalletInteractorImpl
import jp.co.soramitsu.wallet.impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.wallet.impl.domain.beacon.BeaconSharedState
import jp.co.soramitsu.wallet.impl.domain.implementations.ExistentialDepositUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.implementations.TokenUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixinProvider
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigProvider
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class WalletFeatureModule {

    @Provides
    fun provideHistoryApi(networkApiCreator: NetworkApiCreator): OperationsHistoryApi {
        return networkApiCreator.create(OperationsHistoryApi::class.java)
    }

    @Provides
    fun provideRemoteConfigFetcher(networkApiCreator: NetworkApiCreator): RemoteConfigFetcher {
        return networkApiCreator.create(RemoteConfigFetcher::class.java)
    }

    @Provides
    fun provideAssetCache(
        tokenPriceDao: TokenPriceDao,
        assetDao: AssetDao,
        accountRepository: AccountRepository,
        updatesMixin: UpdatesMixin
    ): AssetCache {
        return AssetCache(tokenPriceDao, accountRepository, assetDao, updatesMixin)
    }

    @Provides
    fun providePhishingApi(networkApiCreator: NetworkApiCreator): PhishingApi {
        return networkApiCreator.create(PhishingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHistoryFiltersProvider() = HistoryFiltersProvider()

    @Provides
    fun provideSubstrateSource(
        rpcCalls: RpcCalls,
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        extrinsicService: ExtrinsicService
    ): SubstrateRemoteSource = WssSubstrateSource(
        rpcCalls,
        remoteStorageSource,
        extrinsicService
    )

    @Provides
    fun provideTokenRepository(
        tokenPriceDao: TokenPriceDao
    ): TokenRepository = TokenRepositoryImpl(
        tokenPriceDao
    )

    @Provides
    fun provideCursorStorage(preferences: Preferences) = TransferCursorStorage(preferences)

    @Provides
    @Singleton
    fun provideWalletRepository(
        substrateSource: SubstrateRemoteSource,
        operationsDao: OperationDao,
        httpExceptionHandler: HttpExceptionHandler,
        phishingApi: PhishingApi,
        phishingDao: PhishingDao,
        walletConstants: WalletConstants,
        assetCache: AssetCache,
        coingeckoApi: CoingeckoApi,
        chainRegistry: ChainRegistry,
        availableFiatCurrencies: GetAvailableFiatCurrencies,
        updatesMixin: UpdatesMixin,
        remoteConfigFetcher: RemoteConfigFetcher
        soraRemoteConfigBuilder: SoraRemoteConfigBuilder
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        operationsDao,
        httpExceptionHandler,
        phishingApi,
        assetCache,
        walletConstants,
        phishingDao,
        coingeckoApi,
        chainRegistry,
        availableFiatCurrencies,
        updatesMixin,
        remoteConfigFetcher,
        soraRemoteConfigBuilder
    )

    @Provides
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        addressBookRepository: AddressBookRepository,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        fileProvider: FileProvider,
        preferences: Preferences,
        selectedFiat: SelectedFiat,
        updatesMixin: UpdatesMixin
    ): WalletInteractor = WalletInteractorImpl(
        walletRepository,
        addressBookRepository,
        accountRepository,
        chainRegistry,
        fileProvider,
        preferences,
        selectedFiat,
        updatesMixin
    )

    @Provides
    fun provideExistentialDepositUseCase(
        chainRegistry: ChainRegistry,
        rpcCalls: RpcCalls
    ): ExistentialDepositUseCase = ExistentialDepositUseCaseImpl(chainRegistry, rpcCalls)

    @Provides
    fun provideValidateTransferUseCase(
        existentialDepositUseCase: ExistentialDepositUseCase,
        walletConstants: WalletConstants,
        chainRegistry: ChainRegistry,
        walletInteractor: WalletInteractor,
        substrateSource: SubstrateRemoteSource
    ): ValidateTransferUseCase = ValidateTransferUseCaseImpl(
        existentialDepositUseCase,
        walletConstants,
        chainRegistry,
        walletInteractor,
        substrateSource
    )

    @Provides
    fun provideChainInteractor(
        chainDao: ChainDao
    ): ChainInteractor = ChainInteractor(chainDao)

    @Provides
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
        chainRegistry: ChainRegistry
    ): BuyMixin.Presentation = BuyMixinProvider(buyTokenRegistry, chainRegistry)

    @Provides
    fun provideTransferChecks(): TransferValidityChecks.Presentation = TransferValidityChecksProvider()

    @Provides
    fun providePaymentUpdaterFactory(
        remoteSource: SubstrateRemoteSource,
        assetCache: AssetCache,
        operationDao: OperationDao,
        accountUpdateScope: AccountUpdateScope,
        chainRegistry: ChainRegistry,
        updatesMixin: UpdatesMixin
    ) = PaymentUpdaterFactory(
        remoteSource,
        assetCache,
        operationDao,
        chainRegistry,
        accountUpdateScope,
        updatesMixin
    )

    @Provides
    @Singleton
    @Named("BalancesUpdateSystem")
    fun provideFeatureUpdaters(
        chainRegistry: ChainRegistry,
        paymentUpdaterFactory: PaymentUpdaterFactory,
        accountRepository: AccountRepository
    ): UpdateSystem = BalancesUpdateSystem(
        chainRegistry,
        paymentUpdaterFactory,
        accountRepository
    )

    @Provides
    fun provideWalletConstants(
        chainRegistry: ChainRegistry
    ): WalletConstants = RuntimeWalletConstants(chainRegistry)

    @Provides
    fun provideAccountAddressUseCase(accountRepository: AccountRepository, chainRegistry: ChainRegistry) =
        CurrentAccountAddressUseCase(accountRepository, chainRegistry)

    @Provides
    @Singleton
    fun provideBeaconApi(
        gson: Gson,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        preferences: Preferences,
        extrinsicService: ExtrinsicService,
        beaconSharedState: BeaconSharedState
    ) = BeaconInteractor(gson, accountRepository, chainRegistry, preferences, extrinsicService, beaconSharedState)

    @Provides
    fun provideFeeLoaderMixin(
        resourceManager: ResourceManager,
        tokenUseCase: TokenUseCase
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(
        resourceManager,
        tokenUseCase
    )

    @Provides
    fun provideTokenUseCase(
        tokenRepository: TokenRepository,
        sharedState: BeaconSharedState
    ): TokenUseCase = TokenUseCaseImpl(
        tokenRepository,
        sharedState
    )

    @Provides
    @Singleton
    fun provideBeaconSharedState(
        chainRegistry: ChainRegistry,
        preferences: Preferences
    ): BeaconSharedState = BeaconSharedState(chainRegistry, preferences)

    @Provides
    @Singleton
    fun provideSendSharedState() = SendSharedState()

    @Provides
    fun provideQrCodeDecoder(contentResolver: ContentResolver): QrBitmapDecoder {
        return QrBitmapDecoder(contentResolver)
    }

    @Provides
    @Singleton
    fun provideAddressBookRepository(
        addressBookDao: AddressBookDao
    ): AddressBookRepository = AddressBookRepositoryImpl(addressBookDao)

    @Singleton
    @Provides
    fun provideSoramitsuNetworkClient(): SoramitsuNetworkClient =
        SoramitsuNetworkClient(logging = BuildConfig.DEBUG)

    @Singleton
    @Provides
    fun provideSubQueryClientForSoraWalletFactory(
        @ApplicationContext context: Context
    ): SubQueryClientForSoraWalletFactory = SubQueryClientForSoraWalletFactory(context)

    @Singleton
    @Provides
    fun provideSoraRemoteConfigBuilder(
        client: SoramitsuNetworkClient,
        @ApplicationContext context: Context
    ): SoraRemoteConfigBuilder {
        return SoraRemoteConfigProvider(
            context = context,
            client = client,
            commonUrl = "",
            mobileUrl = ""
        ).provide()
    }
}
