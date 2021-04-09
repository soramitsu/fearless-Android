package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.RampProvider
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsicFactory
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.AccountBalanceUpdater
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters.AccountInfoSchemaUpdater
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
    fun provideTripleRefCountProperty() = SuspendableProperty<Boolean>()

    @Provides
    @FeatureScope
    fun provideExtrinsicFactory(
        signer: Signer
    ) = TransferExtrinsicFactory(signer)

    @Provides
    @FeatureScope
    fun provideAccountInfoFactory(
        tripleRefCountProperty: SuspendableProperty<Boolean>
    ) = AccountInfoFactory(tripleRefCountProperty)

    @Provides
    @FeatureScope
    fun provideSubstrateSource(
        socketService: SocketService,
        keypairFactory: KeypairFactory,
        accountInfoFactory: AccountInfoFactory,
        extrinsicFactory: TransferExtrinsicFactory,
        sS58Encoder: SS58Encoder
    ) = WssSubstrateSource(
        socketService,
        keypairFactory,
        accountInfoFactory,
        extrinsicFactory,
        sS58Encoder
    )

    @Provides
    @FeatureScope
    fun provideWalletRepository(
        substrateSource: WssSubstrateSource,
        accountRepository: AccountRepository,
        assetDao: AssetDao,
        transactionDao: TransactionDao,
        subscanNetworkApi: SubscanNetworkApi,
        sS58Encoder: SS58Encoder,
        httpExceptionHandler: HttpExceptionHandler
    ): WalletRepository = WalletRepositoryImpl(
        substrateSource,
        accountRepository,
        assetDao,
        transactionDao,
        subscanNetworkApi,
        sS58Encoder,
        httpExceptionHandler
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
        tripleRefCountProperty: SuspendableProperty<Boolean>,
        socketService: SocketService
    ): AccountInfoSchemaUpdater {
        return AccountInfoSchemaUpdater(tripleRefCountProperty, socketService)
    }

    @Provides
    @FeatureScope
    fun provideBalanceUpdater(
        accountRepository: AccountRepository,
        walletRepository: WalletRepository
    ): AccountBalanceUpdater {
        return AccountBalanceUpdater(accountRepository, walletRepository)
    }

    @Provides
    @FeatureScope
    fun provideFeatureUpdaters(
        schemaUpdater: AccountInfoSchemaUpdater,
        balanceUpdater: AccountBalanceUpdater
    ): WalletUpdaters = WalletUpdaters(listOf(schemaUpdater, balanceUpdater))
}