package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.SingleStorageKeyUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope

class AccountControllerBalanceUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) : SingleStorageKeyUpdater<AccountStakingScope>(scope, runtimeProperty, storageCache) {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        val stakingAccessInfo = scope.getAccountStaking().stakingAccessInfo ?: return null
        val stashId = stakingAccessInfo.controllerId

        return runtime.metadata.system().storage("Account").storageKey(runtime, stashId)
    }
}
