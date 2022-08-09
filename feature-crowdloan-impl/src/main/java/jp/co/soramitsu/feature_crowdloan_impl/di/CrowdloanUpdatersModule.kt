package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.BlockNumberUpdater
import jp.co.soramitsu.runtime.network.updaters.SingleChainUpdateSystem
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class CrowdloanUpdatersModule {

    @Provides
    @Singleton
    fun provideBlockNumberUpdater(
        chainRegistry: ChainRegistry,
        crowdloanSharedState: CrowdloanSharedState,
        storageCache: StorageCache,
    ) = BlockNumberUpdater(chainRegistry, crowdloanSharedState, storageCache)

    @Provides
    fun provideCrowdloanUpdateSystem(
        chainRegistry: ChainRegistry,
        crowdloanSharedState: CrowdloanSharedState,
        blockNumberUpdater: BlockNumberUpdater,
    ): UpdateSystem = SingleChainUpdateSystem(
        updaters = listOf(
            blockNumberUpdater
        ),
        chainRegistry = chainRegistry,
        singleAssetSharedState = crowdloanSharedState
    )
}
