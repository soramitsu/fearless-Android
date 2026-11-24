package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.historical

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.fetchValuesToCache
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.observeActiveEraIndex
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.historicalEras
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

interface HistoricalUpdater {

    fun constructHistoricalKey(runtime: RuntimeSnapshot, era: BigInteger): String
}

class HistoricalUpdateMediator(
    private val historicalUpdaters: List<HistoricalUpdater>,
    private val stakingSharedState: StakingSharedState,
    private val bulkRetriever: BulkRetriever,
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val storageCache: StorageCache,
    private val chainRegistry: ChainRegistry
) : GlobalScopeUpdater {

    override val requiredModules: List<String> = listOf(Modules.STAKING)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chainId = stakingSharedState.chainId()
        val runtime = chainRegistry.getRuntime(chainId)

        return storageCache.observeActiveEraIndex(runtime, chainId)
            .onEach { activeEra -> fetchMissingHistory(chainId, runtime, activeEra, storageSubscriptionBuilder.socketService) }
            .noSideAffects()
    }

    /**
     * Caches the history of the eras before [activeEra] that is not cached yet. The range follows the era this updater
     * observes, so no remote era read can end the update system. A round that fails is left for the next era change;
     * only the update system's own cancellation ends the updater.
     */
    private suspend fun fetchMissingHistory(
        chainId: ChainId,
        runtime: RuntimeSnapshot,
        activeEra: BigInteger,
        socketService: SocketService
    ) {
        val round = runCatching {
            val allKeysNeeded = constructHistoricalKeys(chainId, runtime, activeEra)
            val keysInDataBase = storageCache.filterKeysInCache(allKeysNeeded, chainId).toSet()
            val missingKeys = allKeysNeeded.filter { it !in keysInDataBase }
            if (missingKeys.isNotEmpty()) {
                bulkRetriever.fetchValuesToCache(socketService, missingKeys, storageCache, chainId)
            }
        }
        // A failed round only leaves the history uncached until the next era; the update system's cancellation ends it.
        if (round.isFailure) currentCoroutineContext().ensureActive()
    }

    private suspend fun constructHistoricalKeys(chainId: ChainId, runtime: RuntimeSnapshot, activeEra: BigInteger): List<String> {
        val historicalRange = stakingRepository.historicalEras(chainId, activeEra)

        return historicalUpdaters.map { updater ->
            historicalRange.map { updater.constructHistoricalKey(runtime, it) }
        }.flatten()
    }
}
