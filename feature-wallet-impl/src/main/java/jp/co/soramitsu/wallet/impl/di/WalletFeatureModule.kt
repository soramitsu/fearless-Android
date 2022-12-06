package jp.co.soramitsu.wallet.impl.di

import android.content.ContentResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
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
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecksProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.MoonPayProvider
import jp.co.soramitsu.wallet.impl.data.buyToken.RampProvider
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalancesUpdateSystem
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.PaymentUpdaterFactory
import jp.co.soramitsu.wallet.impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.wallet.impl.data.network.subquery.SubQueryOperationsApi
import jp.co.soramitsu.wallet.impl.data.repository.AddressBookRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.repository.RuntimeWalletConstants
import jp.co.soramitsu.wallet.impl.data.repository.TokenRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.WalletInteractorImpl
import jp.co.soramitsu.wallet.impl.domain.implementations.ExistentialDepositUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixinProvider
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.send.recipient.QrBitmapDecoder
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.HistoryFiltersProvider

@InstallIn(SingletonComponent::class)
@Module
class WalletFeatureModule {

    @Provides
    fun provideSubQueryApi(networkApiCreator: NetworkApiCreator): SubQueryOperationsApi {
        return networkApiCreator.create(SubQueryOperationsApi::class.java)
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
        subQueryOperationsApi: SubQueryOperationsApi,
        httpExceptionHandler: HttpExceptionHandler,
        phishingApi: PhishingApi,
        phishingDao: PhishingDao,
        walletConstants: WalletConstants,
        assetCache: AssetCache,
        coingeckoApi: CoingeckoApi,
        cursorStorage: TransferCursorStorage,
        chainRegistry: ChainRegistry,
        availableFiatCurrencies: GetAvailableFiatCurrencies,
        updatesMixin: UpdatesMixin,
        remoteConfigFetcher: RemoteConfigFetcher,
        currentAccountAddressUseCase: CurrentAccountAddressUseCase
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        operationsDao,
        subQueryOperationsApi,
        httpExceptionHandler,
        phishingApi,
        assetCache,
        walletConstants,
        phishingDao,
        cursorStorage,
        coingeckoApi,
        chainRegistry,
        availableFiatCurrencies,
        updatesMixin,
        remoteConfigFetcher,
        currentAccountAddressUseCase
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
        accountUpdateScope: AccountUpdateScope
    ): UpdateSystem = BalancesUpdateSystem(
        chainRegistry,
        paymentUpdaterFactory,
        accountUpdateScope
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
}
