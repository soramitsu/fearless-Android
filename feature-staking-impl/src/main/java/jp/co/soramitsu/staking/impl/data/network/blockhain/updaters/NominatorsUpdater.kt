package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.stakingOrNull
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class NominatorsUpdater(
    private val bulkRetriever: BulkRetriever,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val storageCache: StorageCache
) : GlobalScopeUpdater, StakingUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chainId = stakingSharedState.chainId()
        val runtime = chainRegistry.getRuntime(chainId)

        if (runtime.metadata.stakingOrNull()
                ?.storageOrNull("ErasStakersPaged") == null
        ) return flowOf()

        return storageCache.observeActiveEraIndex(runtime, chainId)
            .map { activeEraIndex ->
                eraStakersPagedPrefix(runtime, activeEraIndex)
            }
            .filterNot { storageCache.isPrefixInCache(it, chainId) }
            .map {
                updateNominatorsForEra(it, storageSubscriptionBuilder.socketService, chainId)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun eraStakersPagedPrefix(
        runtime: RuntimeSnapshot,
        activeEraIndex: BigInteger,
    ): String {
        return runtime.metadata.staking().storage("ErasStakersPaged")
            .storageKey(runtime, activeEraIndex)
    }


    private suspend fun updateNominatorsForEra(
        eraStakersPrefix: String,
        socketService: SocketService,
        chainId: String
    ) = runCatching {
        val allKeys = bulkRetriever.retrieveAllKeys(socketService, eraStakersPrefix)
        val allValues = bulkRetriever.queryKeys(socketService, allKeys)

        val toInsert = allValues.map { (key, value) -> StorageEntry(key, value) }
        storageCache.insert(toInsert, chainId)
    }
}
