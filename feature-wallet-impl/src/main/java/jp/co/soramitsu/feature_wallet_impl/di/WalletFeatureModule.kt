package jp.co.soramitsu.feature_wallet_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.RxWebSocketCreator
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.WssSubstrateSource
import jp.co.soramitsu.feature_wallet_impl.data.network.subscan.SubscanNetworkApi
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.common.AddressIconGenerator

@Module
class WalletFeatureModule {

    @Provides
    @FeatureScope
    fun provideSubscanApi(networkApiCreator: NetworkApiCreator): SubscanNetworkApi {
        return networkApiCreator.create(SubscanNetworkApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideSubstrateSource(
        socketRequestExecutor: SocketSingleRequestExecutor,
        rxWebSocketCreator: RxWebSocketCreator,
        keypairFactory: KeypairFactory,
        signer: Signer,
        sS58Encoder: SS58Encoder
    ) = WssSubstrateSource(socketRequestExecutor, rxWebSocketCreator, signer, keypairFactory, sS58Encoder)

    @Provides
    @FeatureScope
    fun provideWalletRepository(
        substrateSource: WssSubstrateSource,
        accountRepository: AccountRepository,
        assetDao: AssetDao,
        transactionDao: TransactionDao,
        subscanNetworkApi: SubscanNetworkApi
    ): WalletRepository = WalletRepositoryImpl(substrateSource, accountRepository, assetDao, transactionDao, subscanNetworkApi)

    @Provides
    @FeatureScope
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ): WalletInteractor = WalletInteractorImpl(walletRepository, accountRepository)

    @Provides
    @FeatureScope
    fun provideAddressModelCreator(
        interactor: WalletInteractor,
        resourceManager: ResourceManager,
        iconGenerator: IconGenerator
    ): AddressIconGenerator = AddressIconGenerator(interactor, iconGenerator, resourceManager)
}