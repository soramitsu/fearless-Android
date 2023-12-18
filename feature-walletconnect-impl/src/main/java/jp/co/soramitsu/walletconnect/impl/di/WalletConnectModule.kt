package jp.co.soramitsu.walletconnect.impl.di

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
class WalletConnectModule {
    @Provides
    fun provideWalletConnectInteractor(
        chainsRepository: ChainsRepository,
        ethereumSource: EthereumRemoteSource
    ): WalletConnectInteractor = WalletConnectInteractorImpl(
        chainsRepository,
        ethereumSource
    )
}
