package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ValidatorPrefsUpdater(
    private val bulkRetriever: BulkRetriever,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val storageCache: StorageCache
) : GlobalScopeUpdater, StakingUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        return flow {
            val chainId = stakingSharedState.chainId()
            val runtime = chainRegistry.getRuntime(chainId)
            val prefix = runtime.metadata.staking().storage("Validators").storageKey(runtime)
            if (storageCache.isPrefixInCache(prefix, chainId)) {
                return@flow
            }

            val allKeys =
                bulkRetriever.retrieveAllKeys(storageSubscriptionBuilder.socketService, prefix)

            val allValues =
                bulkRetriever.queryKeys(storageSubscriptionBuilder.socketService, allKeys)

            val toInsert = allValues.map { (key, value) -> StorageEntry(key, value) }

            storageCache.insert(toInsert, chainId)

            emit(Unit)
        }.flowOn(Dispatchers.IO)
            .noSideAffects()
    }
}
