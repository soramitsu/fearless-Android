package jp.co.soramitsu.walletconnect.impl.di

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
class WalletConnectModule {
    @Provides
    fun provideWalletConnectInteractor(
        chainsRepository: ChainsRepository
    ): WalletConnectInteractor = WalletConnectInteractorImpl(
        chainsRepository
    )

}
