package jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.updaters.SingleStorageKeyUpdater
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey

class BlockNumberUpdater(
    runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    storageCache: StorageCache
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, runtimeProperty, storageCache) {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return runtime.metadata.system().storage("Number").storageKey()
    }

    override val requiredModules = listOf(Modules.SYSTEM)
}
