package jp.co.soramitsu.liquiditypools.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.navigation.InternalPoolsRouterImpl
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter

@Module
@InstallIn(SingletonComponent::class)
class PoolsModule {

    @Provides
    @Singleton
    fun providesPoolIteractor(
        polkaswapRepository: PolkaswapRepository,
        accountRepository: AccountRepository,
        polkaswapInteractor: PolkaswapInteractor,
        chainRegistry: ChainRegistry,
        keypairProvider: KeypairProvider
    ): PoolsInteractor =
        PoolsInteractorImpl(polkaswapRepository, accountRepository, polkaswapInteractor, chainRegistry, keypairProvider)

    @Provides
    @Singleton
    fun provideInnerLiquidityPoolsRouter(walletRouter: WalletRouter): InternalPoolsRouter = InternalPoolsRouterImpl()
}