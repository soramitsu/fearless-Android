package jp.co.soramitsu.walletconnect.impl.di

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.walletconnect.impl.presentation.PoolEvmTransactionSender
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectInteractorImpl
import jp.co.soramitsu.walletconnect.impl.presentation.chainRegistryRuntimeSource

@InstallIn(SingletonComponent::class)
@Module
class WalletConnectModule {
    @Provides
    fun provideWalletConnectInteractor(
        chainsRepository: ChainsRepository,
        ethereumSource: EthereumRemoteSource,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        chainRegistry: ChainRegistry,
        ethereumConnectionPool: EthereumConnectionPool,
        featureToggles: ProductFeatureToggleStore
    ): WalletConnectInteractor = WalletConnectInteractorImpl(
        chainsRepository = chainsRepository,
        ethereumSource = ethereumSource,
        accountRepository = accountRepository,
        keypairProvider = keypairProvider,
        runtimeSource = chainRegistryRuntimeSource(chainRegistry),
        transactionSender = PoolEvmTransactionSender(ethereumConnectionPool),
        featureToggles = featureToggles
    )
}
