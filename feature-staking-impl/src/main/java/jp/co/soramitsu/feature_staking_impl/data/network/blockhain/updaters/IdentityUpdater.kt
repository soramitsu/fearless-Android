package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.identity
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.ParachainStakingUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater
import kotlinx.coroutines.flow.Flow

class IdentityUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
) : SingleStorageKeyUpdater<AccountStakingScope>(
    scope,
    stakingSharedState,
    chainRegistry,
    storageCache
), ParachainStakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        val accountId = scope.getAccountId() ?: return null
        return runtime.metadata.identity().storage("IdentityOf").storageKey(runtime, accountId)
    }

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        return super.listenForUpdates(storageSubscriptionBuilder)
    }
}
