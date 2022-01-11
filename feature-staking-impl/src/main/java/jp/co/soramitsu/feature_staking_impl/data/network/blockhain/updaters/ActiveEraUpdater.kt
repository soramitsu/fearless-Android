package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater

class ActiveEraUpdater(
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
    storageCache: StorageCache
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, stakingSharedState, chainRegistry, storageCache), StakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return runtime.metadata.staking().storage("ActiveEra").storageKey()
    }
}
