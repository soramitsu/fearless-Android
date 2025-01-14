package jp.co.soramitsu.liquiditypools.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.liquiditypools.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.data.DemeterFarmingRepositoryImpl
import jp.co.soramitsu.liquiditypools.impl.data.PoolsRepositoryImpl
import jp.co.soramitsu.liquiditypools.impl.domain.DemeterFarmingInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.domain.PoolsInteractorImpl
import jp.co.soramitsu.liquiditypools.impl.navigation.InternalPoolsRouterImpl
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class PoolsModule {

    @Provides
    @Singleton
    fun providesPoolInteractor(
        poolsRepository: PoolsRepository,
        accountRepository: AccountRepository,
        blockExplorerManager: BlockExplorerManager
    ): PoolsInteractor = PoolsInteractorImpl(poolsRepository, accountRepository, blockExplorerManager)

    @Provides
    @Singleton
    fun provideInternalLiquidityPoolsRouter(
        walletRouter: WalletRouter,
        resourceManager: ResourceManager
    ): InternalPoolsRouter = InternalPoolsRouterImpl(
        walletRouter = walletRouter,
        resourceManager = resourceManager
    )

    @Provides
    @Singleton
    fun provideDemeterFarmingInteractor(demeterFarmingRepository: DemeterFarmingRepository): DemeterFarmingInteractor =
        DemeterFarmingInteractorImpl(demeterFarmingRepository)

    @Provides
    @Singleton
    fun provideDemeterFarmingRepository(
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
        accountRepository: AccountRepository,
        walletRepository: WalletRepository,
        poolsRepository: PoolsRepository,
    ): DemeterFarmingRepository = DemeterFarmingRepositoryImpl(
        chainRegistry,
        bulkRetriever,
        accountRepository,
        walletRepository,
        poolsRepository
    )

    @Provides
    @Singleton
    fun providePoolsRepositoryImpl(
        extrinsicService: ExtrinsicService,
        chainRegistry: ChainRegistry,
        accountRepository: AccountRepository,
        poolDao: PoolDao,
        appDataBase: AppDatabase
    ): PoolsRepository {
        return PoolsRepositoryImpl(
            extrinsicService,
            chainRegistry,
            accountRepository,
            poolDao,
            appDataBase
        )
    }
}
