package jp.co.soramitsu.liquiditypools.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.data.DemeterFarmingRepositoryImpl
import jp.co.soramitsu.liquiditypools.impl.domain.DemeterFarmingInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.navigation.InternalPoolsRouterImpl
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter

@Module
@InstallIn(SingletonComponent::class)
class PoolsModule {

    @Provides
    @Singleton
    fun providesPoolInteractor(
        polkaswapRepository: PolkaswapRepository,
        accountRepository: AccountRepository,
        polkaswapInteractor: PolkaswapInteractor,
        chainRegistry: ChainRegistry,
        keypairProvider: KeypairProvider
    ): PoolsInteractor =
        PoolsInteractorImpl(polkaswapRepository, accountRepository, polkaswapInteractor, chainRegistry, keypairProvider)

    @Provides
    @Singleton
    fun provideInternalLiquidityPoolsRouter(walletRouter: WalletRouter): InternalPoolsRouter = InternalPoolsRouterImpl(
        walletRouter = walletRouter
    )

    @Provides
    @Singleton
    fun provideDemeterFarmingInteractor(
        demeterFarmingRepository: DemeterFarmingRepository,
    ) : DemeterFarmingInteractor =
        DemeterFarmingInteractorImpl(demeterFarmingRepository)

    @Provides
    @Singleton
    fun provideDemeterFarmingRepository(
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
        accountRepository: AccountRepository,
        walletRepository: WalletRepository,
        polkaswapRepository: PolkaswapRepository,
    ) : DemeterFarmingRepository =
        DemeterFarmingRepositoryImpl(
            chainRegistry,
            bulkRetriever,
            accountRepository,
            walletRepository,
            polkaswapRepository
        )
}