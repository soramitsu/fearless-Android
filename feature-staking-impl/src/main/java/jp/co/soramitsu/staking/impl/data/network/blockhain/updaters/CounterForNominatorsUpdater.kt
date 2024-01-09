package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater

class CounterForNominatorsUpdater(
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
    storageCache: StorageCache
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, stakingSharedState, chainRegistry, storageCache), StakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        return runtime.metadata.moduleOrNull(Modules.STAKING)?.storageOrNull("CounterForNominators")?.storageKey()
    }
}
