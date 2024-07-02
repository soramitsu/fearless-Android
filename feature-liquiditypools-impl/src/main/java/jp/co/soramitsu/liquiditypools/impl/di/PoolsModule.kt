package jp.co.soramitsu.liquiditypools.impl.di

import androidx.navigation.Navigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter

@Module
@InstallIn(SingletonComponent::class)
class PoolsModule {

    @Provides
    @Singleton
    fun providesPoolIteractor(
        polkaswapRepository: PolkaswapRepository
    ): PoolsInteractor =
        PoolsInteractorImpl(polkaswapRepository)

//    @Provides
//    @Singleton
//    fun provideLiquidityPoolsRouter(walletRouter: WalletRouter): InternalNFTRouter = InternalNFTRouterImpl(
//        walletRouter = walletRouter
//    )
}