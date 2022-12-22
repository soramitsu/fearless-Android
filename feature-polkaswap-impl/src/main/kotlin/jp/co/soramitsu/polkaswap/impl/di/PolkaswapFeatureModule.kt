package jp.co.soramitsu.polkaswap.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository

@InstallIn(SingletonComponent::class)
@Module
class PolkaswapFeatureModule {

    @Provides
    fun providePolkaswapInteractor(
        chainRegistry: ChainRegistry,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ): PolkaswapInteractor = PolkaswapInteractorImpl(
        chainRegistry,
        walletRepository,
        accountRepository
    )
}
