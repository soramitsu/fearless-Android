package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.updaters.CrowdloanUpdaters
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.updaters.BlockNumberUpdater

@Module
class CrowdloanUpdatersModule {

    @Provides
    @FeatureScope
    fun provideBlockNumberUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache,
    ) = BlockNumberUpdater(runtimeProperty, storageCache)

    @Provides
    @FeatureScope
    fun provideCrowdloanUpdaters(
        blockNumberUpdater: BlockNumberUpdater,
    ) = CrowdloanUpdaters(
        arrayOf(
            blockNumberUpdater
        )
    )
}
