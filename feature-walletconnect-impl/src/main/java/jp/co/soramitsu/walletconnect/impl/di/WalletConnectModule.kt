package jp.co.soramitsu.walletconnect.impl.di

import co.jp.soramitsu.walletconnect.domain.TonConnectInteractor
import co.jp.soramitsu.walletconnect.domain.TonConnectRouter
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.walletconnect.impl.presentation.TonConnectInteractorImpl
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
class WalletConnectModule {
    @Provides
    fun provideWalletConnectInteractor(
        chainsRepository: ChainsRepository,
        ethereumSource: EthereumRemoteSource,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        extrinsicService: ExtrinsicService
    ): WalletConnectInteractor = WalletConnectInteractorImpl(
        chainsRepository,
        ethereumSource,
        accountRepository,
        keypairProvider,
        extrinsicService
    )

    @Provides
    fun provideTonConnectInteractor(
        chainsRepository: ChainsRepository,
        accountRepository: AccountRepository,
        keypairProvider: KeypairProvider,
        tonApi: TonApi,
        tonConnectRouter: TonConnectRouter
    ): TonConnectInteractor = TonConnectInteractorImpl(
        chainsRepository,
        accountRepository,
        keypairProvider,
        tonApi,
        tonConnectRouter
    )
}
