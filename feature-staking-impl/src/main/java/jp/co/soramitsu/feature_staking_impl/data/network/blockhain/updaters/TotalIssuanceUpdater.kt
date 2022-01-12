package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater

class TotalIssuanceUpdater(
    stakingSharedState: StakingSharedState,
    storageCache: StorageCache,
    chainRegistry: ChainRegistry
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, stakingSharedState, chainRegistry, storageCache) {

    override val requiredModules: List<String> = listOf(Modules.BALANCES)

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return runtime.metadata.balances().storage("TotalIssuance").storageKey()
    }
}
