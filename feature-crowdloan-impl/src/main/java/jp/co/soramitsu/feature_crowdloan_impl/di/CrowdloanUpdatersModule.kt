package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.updaters.BlockNumberUpdater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleChainUpdateSystem

@Module
class CrowdloanUpdatersModule {

    @Provides
    @FeatureScope
    fun provideBlockNumberUpdater(
        chainRegistry: ChainRegistry,
        crowdloanSharedState: CrowdloanSharedState,
        storageCache: StorageCache,
    ) = BlockNumberUpdater(chainRegistry, crowdloanSharedState, storageCache)

    @Provides
    @FeatureScope
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
