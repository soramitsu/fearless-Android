package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.SingleStorageKeyUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater

class HistoryDepthUpdater(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    storageCache: StorageCache,
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, runtimeProperty, storageCache), StakingUpdater {

    override fun fallbackValue(runtime: RuntimeSnapshot): String {
        return storageEntry(runtime).default.toHexString(withPrefix = true)
    }

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return storageEntry(runtime).storageKey()
    }

    private fun storageEntry(runtime: RuntimeSnapshot) = runtime.metadata.staking().storage("HistoryDepth")
}
