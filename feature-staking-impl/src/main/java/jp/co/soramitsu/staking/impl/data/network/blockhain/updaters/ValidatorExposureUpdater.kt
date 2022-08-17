package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class ValidatorExposureUpdater(
    private val bulkRetriever: BulkRetriever,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val storageCache: StorageCache
) : GlobalScopeUpdater, StakingUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chainId = stakingSharedState.chainId()
        val runtime = chainRegistry.getRuntime(chainId)

        return storageCache.observeActiveEraIndex(runtime, chainId)
            .map { eraStakersPrefix(runtime, it) }
            .filterNot { storageCache.isPrefixInCache(it, chainId) }
            .onEach { updateNominatorsForEra(it, storageSubscriptionBuilder.socketService, chainId) }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun eraStakersPrefix(runtime: RuntimeSnapshot, activeEraIndex: BigInteger): String {
        return runtime.metadata.staking().storage("ErasStakers").storageKey(runtime, activeEraIndex)
    }

    private suspend fun updateNominatorsForEra(
        eraStakersPrefix: String,
        socketService: SocketService,
        chainId: String
    ) = runCatching {
        bulkRetriever.fetchPrefixValuesToCache(socketService, eraStakersPrefix, storageCache, chainId)
    }
}
