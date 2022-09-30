package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.parachainStakingOrNull
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.ParachainStakingUpdater
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater
import kotlinx.coroutines.flow.Flow

class DelegatorStateUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry
) : SingleStorageKeyUpdater<AccountStakingScope>(scope, stakingSharedState, chainRegistry, storageCache), ParachainStakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        val accountId = scope.getAccountId() ?: return null
        return runtime.metadata.parachainStakingOrNull()?.storage("DelegatorState")?.storageKey(runtime, accountId)
    }

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        return super.listenForUpdates(storageSubscriptionBuilder)
    }
}
