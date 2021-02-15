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
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.RampProvider
import jp.co.soramitsu.feature_wallet_impl.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsicFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.AccountInfoSchemaUpdater
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.PaymentUpdater
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.StakingLedgerUpdater
import jp.co.soramitsu.feature_wallet_impl.data.network.phishing.PhishingApi
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.BuyMixinProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecksProvider

@Module
class WalletFeatureModule {

    @Provides
    @FeatureScope
    fun provideSubscanApi(networkApiCreator: NetworkApiCreator): SubscanNetworkApi {
        return networkApiCreator.create(SubscanNetworkApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideAssetCache(assetDao: AssetDao): AssetCache {
        return AssetCache(assetDao)
    }

    @Provides
    @FeatureScope
    fun provideDualRefCountProperty() = SuspendableProperty<Boolean>()

    @Provides
    @FeatureScope
    fun provideExtrinsicFactory(
        dualRefCountProperty: SuspendableProperty<Boolean>,
        signer: Signer
    ) = TransferExtrinsicFactory(dualRefCountProperty, signer)

    @Provides
    @FeatureScope
    fun provideAccountInfoFactory(
        dualRefCountProperty: SuspendableProperty<Boolean>
    ) = AccountInfoFactory(dualRefCountProperty)

    @Provides
    @FeatureScope
    fun providePhishingApi(networkApiCreator: NetworkApiCreator): PhishingApi {
        return networkApiCreator.create(PhishingApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideSubstrateSource(
        socketService: SocketService,
        keypairFactory: KeypairFactory,
        accountInfoFactory: AccountInfoFactory,
        extrinsicFactory: TransferExtrinsicFactory,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        sS58Encoder: SS58Encoder
    ): SubstrateRemoteSource = WssSubstrateSource(
        socketService,
        keypairFactory,
        accountInfoFactory,
        extrinsicFactory,
        runtimeProperty,
        sS58Encoder
    )

    @Provides
    @FeatureScope
    fun provideWalletRepository(
        substrateSource: SubstrateRemoteSource,
        accountRepository: AccountRepository,
        transactionDao: TransactionDao,
        subscanNetworkApi: SubscanNetworkApi,
        sS58Encoder: SS58Encoder,
        httpExceptionHandler: HttpExceptionHandler,
        phishingApi: PhishingApi,
        phishingAddressDao: PhishingAddressDao,
        assetCache: AssetCache
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        accountRepository,
        transactionDao,
        subscanNetworkApi,
        sS58Encoder,
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
        fileProvider: FileProvider
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
        buyTokenRegistry: BuyTokenRegistry
    ): BuyMixin.Presentation = BuyMixinProvider(buyTokenRegistry)

    @Provides
    @FeatureScope
    fun provideTransferChecks(): TransferValidityChecks.Presentation = TransferValidityChecksProvider()

    @Provides
    @FeatureScope
    fun provideAccountSchemaUpdater(
        accountInfoFactory: AccountInfoFactory,
        socketService: SocketService
    ): AccountInfoSchemaUpdater {
        return AccountInfoSchemaUpdater(accountInfoFactory, socketService)
    }

    @Provides
    @FeatureScope
    fun providePaymentUpdater(
        accountRepository: AccountRepository,
        remoteSource: SubstrateRemoteSource,
        sS58Encoder: SS58Encoder,
        assetCache: AssetCache,
        transactionDao: TransactionDao
    ): PaymentUpdater {
        return PaymentUpdater(
            accountRepository,
            remoteSource,
            sS58Encoder,
            assetCache,
            transactionDao
        )
    }

    @Provides
    @FeatureScope
    fun provideStakingUpdater(
        accountRepository: AccountRepository,
        remoteSource: SubstrateRemoteSource,
        assetCache: AssetCache
    ): StakingLedgerUpdater {
        return StakingLedgerUpdater(
            accountRepository,
            remoteSource,
            assetCache
        )
    }

    @Provides
    @FeatureScope
    fun provideFeatureUpdaters(
        schemaUpdater: AccountInfoSchemaUpdater,
        balanceUpdater: PaymentUpdater,
        stakingLedgerUpdater: StakingLedgerUpdater
    ): WalletUpdaters = WalletUpdaters(arrayOf(schemaUpdater, balanceUpdater, stakingLedgerUpdater))
}