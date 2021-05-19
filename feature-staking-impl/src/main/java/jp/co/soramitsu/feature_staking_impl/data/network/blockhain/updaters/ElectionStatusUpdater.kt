package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.SingleStorageKeyUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater

class ElectionStatusUpdater(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    storageCache: StorageCache,
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, runtimeProperty, storageCache), StakingUpdater {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        val electionStorage = runtime.metadata.moduleOrNull("ElectionProviderMultiPhase")?.storage("CurrentPhase")
            ?: runtime.metadata.staking().storage("EraElectionStatus")

        return electionStorage.storageKey()
    }
}
