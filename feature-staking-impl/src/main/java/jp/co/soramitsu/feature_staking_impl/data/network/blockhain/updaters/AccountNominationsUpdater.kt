package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.updaters.SingleStorageKeyUpdater
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope

class AccountNominationsUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) : SingleStorageKeyUpdater<AccountStakingScope>(scope, runtimeProperty, storageCache), StakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
        val stakingAccessInfo = scope.getAccountStaking().stakingAccessInfo ?: return null
        val stashId = stakingAccessInfo.stashId

        return runtime.metadata.staking().storage("Nominators").storageKey(runtime, stashId)
    }
}
