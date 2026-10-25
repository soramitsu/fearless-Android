package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana

import android.util.Log
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChainOrNull
import jp.co.soramitsu.runtime.multiNetwork.chain.mapNodeLocalToNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Holds the active runtime provider and connection for a chain.
 */
data class ChainService(
    val runtimeProvider: RuntimeProvider,
    val connection: ChainConnection
)

/**
 * Selects connection/runtime work from current user needs and supported live features only.
 * Historical crowdloan flags are deliberately absent and cannot activate a network.
 *
 * [chainIdsNeedingSync] are the chains holding an explicitly shown or a funded asset, so every
 * balance the portfolio shows or reports keeps a live subscription. A ranked testnet is not popular:
 * it syncs through such an asset, or because a live feature offers it (Westend is a staking network).
 */
internal fun selectChainsForBackgroundSync(
    chains: List<Chain>,
    chainIdsNeedingSync: Set<ChainId>,
    isTestnet: (Chain) -> Boolean = Chain::isTestNet
): List<Chain> {
    val popularChains = chains.filter { it.rank != null && !isTestnet(it) }
    val enabledChains = chains.filter { it.id in chainIdsNeedingSync }
    val chainsWithStaking = chains.filter { chain ->
        chain.assets.any { asset ->
            asset.staking == Asset.StakingType.PARACHAIN ||
                asset.staking == Asset.StakingType.RELAYCHAIN ||
                asset.supportStakingPool
        }
    }
    val requiredIdentityChainIds = chains.mapNotNull(Chain::identityChain).toSet()
    val identityHolders = chains.filter { it.id in requiredIdentityChainIds }

    return (popularChains + enabledChains + chainsWithStaking + identityHolders)
        .distinctBy(Chain::id)
        .filter { it.nodes.isNotEmpty() || it.isUniversalWalletBitcoin() }
}

/**
 * Central registry coordinating chain runtimes, connections, and sync lifecycle.
 *
 * Responsibilities:
 * - Tracks the set of chains to keep in-sync (based on DB + enabled assets).
 * - Creates/removes runtime providers and websocket/EVM connections.
 * - Exposes flows for currently synced chains and nodes.
 * - Acknowledges explicit durable node commands through the shared database writer lane.
 */
class ChainRegistry @Inject constructor(
    private val runtimeProviderPool: RuntimeProviderPool,
    private val connectionPool: ConnectionPool,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool,
    private val chainDao: ChainDao,
    private val chainSyncService: ChainSyncService,
    private val runtimeSyncService: RuntimeSyncService,
    private val networkStateService: NetworkStateService,
    private val ethereumConnectionPool: EthereumConnectionPool,
    assetsCache: AssetReadOnlyCache,
    private val chainsRepository: ChainsRepository,
    private val chainEnvironmentConfiguratorProvider: ChainEnvironmentConfiguratorProvider,
    private val portfolioVisibility: PortfolioVisibility,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : IChainRegistry {

    // Background scope for registry work; uses Default dispatcher + supervisor for isolation
    val scope = CoroutineScope(dispatcher + SupervisorJob())

    val syncedChains = MutableStateFlow<List<Chain>>(emptyList())

    // A known empty catalog must be observable, while subscribers still wait for
    // the first completed database snapshot rather than a synthetic initial value.
    private val observedCatalog = MutableStateFlow<List<Chain>?>(null)

    val currentChains = observedCatalog
        .filterNotNull()
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val chainIdsNeedingSyncFlow = assetsCache.observeChainIdsNeedingSync()
        .onStart { emit(emptySet()) }

    // Determines chains that should be actively synced based on popularity, shown or funded assets,
    // and presence of staking or identity requirements.
    private val chainsToSync = chainDao.joinChainInfoFlow()
        .map { chains -> chains.mapNotNull(::mapChainLocalToChainOrNull) }
        .combine(chainIdsNeedingSyncFlow) { chains, chainIdsNeedingSync ->
            selectChainsForBackgroundSync(
                chains = chains,
                chainIdsNeedingSync = chainIdsNeedingSync,
                isTestnet = portfolioVisibility::isTestnet
            )
        }
        .diffed()
        .flowOn(dispatcher)

    private val syncStarted = AtomicBoolean()

    // Note: call `syncUp()` to start observing/maintaining chain connections.

    /**
     * Starts observing DB changes and (re)configuring connections and runtimes accordingly.
     */
    fun syncUp() {
        if (!syncStarted.compareAndSet(false, true)) return
        try {
            chainsToSync.onEach { (removed, addedOrModified, all) ->
            coroutineScope {
                val addedIds = addedOrModified.map(Chain::id).toSet()
                val removedTrue = removed.filterNot { it.id in addedIds } // skip chains that are just updated

                val removedDeferred = removedTrue.map {
                    async { stopChain(it) }
                }

                val syncDeferred = addedOrModified.map { chain ->
                    async {
                        try {
                            setupChain(chain)
                            networkStateService.notifyChainSyncSuccess(chain.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            networkStateService.notifyChainSyncProblem(chain.id)
                            Log.e("ChainRegistry", "Chain setup failed: ${error.javaClass.simpleName}")
                        }
                    }
                }

                (removedDeferred + syncDeferred).awaitAll()
                coroutineContext.job.invokeOnCompletion {
                    val errorMessage = it?.let { error -> "with error: ${error.javaClass.simpleName}" } ?: ""
                    Log.d("ChainRegistry", "chains sync completed $errorMessage")
                }
            }
            this@ChainRegistry.syncedChains.emit(all)
            observedCatalog.value = all

        }
                .launchIn(scope)
                .invokeOnCompletion { syncStarted.set(false) }
        } catch (error: Exception) {
            syncStarted.set(false)
            throw error
        }
    }

    /**
     * Stops and removes all resources for the given chain (EVM or Substrate).
     */
    fun stopChain(chain: Chain) {
        val chainId = chain.id
        if (chain.isEthereumChain) {
            ethereumConnectionPool.stop(chainId)
            return
        }
        runtimeProviderPool.removeRuntimeProvider(chainId)
        runtimeSubscriptionPool.removeSubscription(chainId)
        runtimeSyncService.unregisterChain(chainId)
        connectionPool.removeConnection(chainId)
    }

    /**
     * Applies environment-specific configuration and establishes connections/runtime providers.
     */
    suspend fun setupChain(chain: Chain) {
        chainEnvironmentConfiguratorProvider.provide(chain).configure(chain)
    }

    /**
     * Verifies whether a chain currently has an active connection and (for Substrate) a runtime.
     */
    suspend fun checkChainSyncedUp(chain: Chain): Boolean {
        if (chain.isEthereumChain) {
            return ethereumConnectionPool.getOrNull(chain.id) != null
        }
        val runtime = runtimeProviderPool.getRuntimeProviderOrNull(chain.id)?.getOrNull()

        return connectionPool.getConnectionOrNull(chain.id) != null && runtime != null
    }

    suspend fun getAsset(chainId: ChainId, chainAssetId: String): Asset? {
        return getChain(chainId).assetsById[chainAssetId]
    }

    override suspend fun getChain(chainId: ChainId): Chain {
        return chainsRepository.getChain(chainId)
    }

    override suspend fun getChains(): List<Chain> {
        return chainsRepository.getChains()
    }

    fun nodesFlow(chainId: String) = chainDao.nodesFlow(chainId)
        .mapList(::mapNodeLocalToNode)

    /** Admit synchronously at the user-action boundary, before probes or queued work. */
    fun admitNodeMutation(chainId: ChainId): NodeMutation = NodeMutation(
        chainId,
        chainDao.endpointMutationLane,
        chainDao.endpointMutationLane.admit(listOf(chainId))
    )

    suspend fun switchNode(id: NodeId) = switchNode(admitNodeMutation(id.chainId), id)

    /** Acknowledges the durable selection; runtime convergence follows the database snapshot. */
    suspend fun switchNode(mutation: NodeMutation, id: NodeId) {
        mutation.requireCurrent(id.chainId, chainDao.endpointMutationLane)
        withContext(dispatcher) {
            chainDao.selectNode(mutation.operation, id.chainId, id.nodeUrl)
        }
    }

    suspend fun addNode(chainId: ChainId, nodeName: String, nodeUrl: String) =
        addNode(admitNodeMutation(chainId), chainId, nodeName, nodeUrl)

    suspend fun addNode(mutation: NodeMutation, chainId: ChainId, nodeName: String, nodeUrl: String) {
        mutation.requireCurrent(chainId, chainDao.endpointMutationLane)
        chainDao.insertChainNode(
            mutation.operation,
            ChainNodeLocal(chainId, nodeUrl, nodeName, isActive = false, isDefault = false)
        )
    }

    suspend fun deleteNode(id: NodeId) = deleteNode(admitNodeMutation(id.chainId), id)

    suspend fun deleteNode(mutation: NodeMutation, id: NodeId) {
        mutation.requireCurrent(id.chainId, chainDao.endpointMutationLane)
        chainDao.deleteNode(mutation.operation, id.chainId, id.nodeUrl)
    }

    suspend fun getNode(id: NodeId) = mapNodeLocalToNode(chainDao.getNode(id.chainId, id.nodeUrl))

    suspend fun updateNode(id: NodeId, name: String, url: String) =
        updateNode(admitNodeMutation(id.chainId), id, name, url)

    suspend fun updateNode(mutation: NodeMutation, id: NodeId, name: String, url: String) {
        mutation.requireCurrent(id.chainId, chainDao.endpointMutationLane)
        check(chainDao.updateNode(mutation.operation, id.chainId, id.nodeUrl, name, url) == 1) {
            "Node is no longer available"
        }
    }

    suspend fun getRemoteRuntimeVersion(chainId: ChainId): Int? {
        return chainDao.runtimeInfo(chainId)?.remoteVersion
    }

    suspend fun chainWithAsset(
        chainId: ChainId,
        assetId: String
    ): Pair<Chain, Asset> {
        val chain = getChain(chainId)

        return chain to chain.assetsById.getValue(assetId)
    }

    override fun getConnection(chainId: String) = connectionPool.getConnectionOrThrow(chainId)

    suspend fun awaitConnection(chainId: ChainId) = connectionPool.awaitConnection(chainId)
    @Deprecated(
        "Since we have ethereum chains, which don't have runtime, we must use the function with nullable return value",
        ReplaceWith("getRuntimeOrNull(chainId)")
    )
    override suspend fun getRuntime(chainId: ChainId): RuntimeSnapshot {
        return awaitRuntimeProvider(chainId).get()
    }

    suspend fun getRuntimeOrNull(chainId: ChainId): RuntimeSnapshot? {
        return getRuntimeProviderOrNull(chainId)?.getOrNull()
    }

    suspend fun awaitRuntimeProvider(chainId: String): RuntimeProvider {
        return runtimeProviderPool.awaitRuntimeProvider(chainId)
    }

    fun getRuntimeProviderOrNull(chainId: String): RuntimeProvider? {
        return runtimeProviderPool.getRuntimeProviderOrNull(chainId)
    }

    fun getEthereumConnectionOrNull(chainId: String) = ethereumConnectionPool.getOrNull(chainId)
    suspend fun awaitEthereumConnection(chainId: String) = ethereumConnectionPool.await(chainId)

    suspend fun getService(chainId: ChainId): ChainService {
        return ChainService(
            runtimeProvider = awaitRuntimeProvider(chainId),
            connection = getConnection(chainId)
        )
    }
}
