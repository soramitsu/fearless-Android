package jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScope
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_crowdloan_impl.data.CrowdloanSharedState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.SingleStorageKeyUpdater

class BlockNumberUpdater(
    chainRegistry: ChainRegistry,
    crowdloanSharedState: CrowdloanSharedState,
    storageCache: StorageCache
) : SingleStorageKeyUpdater<GlobalScope>(GlobalScope, crowdloanSharedState, chainRegistry, storageCache) {

    override suspend fun storageKey(runtime: RuntimeSnapshot): String {
        return runtime.metadata.system().storage("Number").storageKey()
    }

    override val requiredModules = listOf(Modules.SYSTEM)
}
