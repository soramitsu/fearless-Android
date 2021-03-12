package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.insert
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach

class AccountNominationsUpdater(
    override val scope: AccountStakingScope,
    private val storageCache: StorageCache,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) : Updater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val stakingAccessInfo = scope.getAccountStaking().stakingAccessInfo ?: return emptyFlow()

        val stashId = stakingAccessInfo.stashId
        val runtime = runtimeProperty.get()

        val storageKey = runtime.metadata.staking().storage("Nominators").storageKey(runtime, stashId)

        return storageSubscriptionBuilder.subscribe(storageKey)
            .onEach(storageCache::insert)
            .noSideAffects()
    }
}
