package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater

class AccountNominationsUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
) : SingleStorageKeyUpdater<AccountStakingScope>(scope, stakingSharedState, chainRegistry, storageCache), StakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        val stakingAccessInfo = scope.getAccountStaking().stakingAccessInfo ?: return null
        val stashId = stakingAccessInfo.stashId

        return runtime.metadata.staking().storage("Nominators").storageKey(runtime, stashId)
    }
}
