package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.SingleStorageKeyUpdater

class ElectionStatusUpdater(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    storageCache: StorageCache,
) : SingleStorageKeyUpdater(runtimeProperty, storageCache) {

    override fun storageKey(runtime: RuntimeSnapshot): String {
        return runtime.metadata.staking().storage("EraElectionStatus").storageKey()
    }
}
