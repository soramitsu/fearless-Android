package jp.co.soramitsu.crowdloan.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.crowdloan.impl.data.CrowdloanSharedState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.BlockNumberUpdater
import jp.co.soramitsu.runtime.network.updaters.SingleChainUpdateSystem
import kotlinx.coroutines.flow.map

@InstallIn(SingletonComponent::class)
@Module
class CrowdloanUpdatersModule {

    @Provides
    @Singleton
    @Named("CrowdloanBlockNumberUpdater")
    fun provideBlockNumberUpdater(
        chainRegistry: ChainRegistry,
        crowdloanSharedState: CrowdloanSharedState,
        storageCache: StorageCache
    ) = BlockNumberUpdater(chainRegistry, crowdloanSharedState, storageCache)

    @Provides
    @Named("CrowdloanChainUpdateSystem")
    fun provideCrowdloanUpdateSystem(
        chainRegistry: ChainRegistry,
        crowdloanSharedState: CrowdloanSharedState,
        @Named("CrowdloanBlockNumberUpdater") blockNumberUpdater: BlockNumberUpdater
    ): UpdateSystem = SingleChainUpdateSystem(
        updaters = listOf(
            blockNumberUpdater
        ),
        chainRegistry = chainRegistry,
        chainFlow = crowdloanSharedState.assetWithChain.map { it.chain }
    )
}
