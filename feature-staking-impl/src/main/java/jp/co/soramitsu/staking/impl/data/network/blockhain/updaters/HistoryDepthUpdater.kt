package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.defaultInHex
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalUpdaterScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater

class HistoryDepthUpdater(
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
    storageCache: StorageCache
) : SingleStorageKeyUpdater<GlobalUpdaterScope>(GlobalUpdaterScope, stakingSharedState, chainRegistry, storageCache), StakingUpdater {

    override fun fallbackValue(runtime: RuntimeSnapshot): String {
        return storageEntry(runtime).defaultInHex()
    }

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return storageEntry(runtime).storageKey()
    }

    private fun storageEntry(runtime: RuntimeSnapshot) = runtime.metadata.staking().storage("HistoryDepth")
}
