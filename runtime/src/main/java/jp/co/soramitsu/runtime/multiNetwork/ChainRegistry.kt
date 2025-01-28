package jp.co.soramitsu.runtime.multiNetwork

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
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
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
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChainService(
    val runtimeProvider: RuntimeProvider,
    val connection: ChainConnection
)

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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : IChainRegistry {

    val scope = CoroutineScope(dispatcher + SupervisorJob())

    val syncedChains = MutableStateFlow<List<Chain>>(emptyList())

    val currentChains = syncedChains
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val enabledAssetsFlow = assetsCache.observeAllEnabledAssets()
        .onStart { emit(emptyList()) }

    private val chainsToSync = chainDao.joinChainInfoFlow()
        .mapList(::mapChainLocalToChain)
        .combine(enabledAssetsFlow) { chains, enabledAssets ->
            val popularChains = chains.filter { it.rank != null }
            val enabledChains =
                enabledAssets.mapNotNull { asset -> chains.find { chain -> chain.id == asset.chainId } }
            val chainsWithCrowdloans = chains.filter { it.hasCrowdloans }
            val chainsWithStaking = chains.filter {
                it.assets.any { asset -> asset.staking == Asset.StakingType.PARACHAIN || asset.staking == Asset.StakingType.RELAYCHAIN || asset.supportStakingPool }
            }
            val identityHolders =
                chains.filter { chain -> chain.identityChain != null }.map { it.identityChain }
                    .mapNotNull { identityChain -> chains.find { it.id == identityChain } }

            (popularChains + enabledChains + chainsWithCrowdloans + chainsWithStaking + identityHolders).toSet()
                .filter { /*it.disabled*/ it.nodes.isNotEmpty() }
        }
        .diffed()
        .filter { it.addedOrModified.isNotEmpty() || it.removed.isNotEmpty() }
        .flowOn(dispatcher)

//    init {
//        syncUp()
//    }

    fun syncUp() {
        chainsToSync.onEach { (removed, addedOrModified, all) ->
            coroutineScope {
                val removedDeferred = removed.map {
                    async { connectionPool.getConnectionOrNull(it.id)?.socketService?.pause() }
                }

                val syncDeferred = addedOrModified.map { chain ->
                    async {
                        runCatching {
                            setupChain(chain)
                        }.onFailure {
                            networkStateService.notifyChainSyncProblem(chain.id)
                            Log.e(
                                "ChainRegistry",
                                "error while sync in chain registry $it"
                            )
                        }.onSuccess {
                            networkStateService.notifyChainSyncSuccess(
                                chain.id
                            )
                        }
                    }
                }

                (removedDeferred + syncDeferred).awaitAll()
                coroutineContext.job.invokeOnCompletion {
                    val errorMessage = it?.let { "with error: ${it.message}" } ?: ""
                    Log.d("ChainRegistry", "chains sync completed $errorMessage")
                }
            }
            this@ChainRegistry.syncedChains.emit(all)

        }
            .launchIn(scope)
    }

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

    suspend fun setupChain(chain: Chain) {
        kotlin.runCatching { chainEnvironmentConfiguratorProvider.provide(chain).configure(chain) }
            .onFailure { Log.d("ChainRegistry", "failed to setup  ${chain.name} chain") }
    }

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

    suspend fun switchNode(id: NodeId) {
        withContext(dispatcher) {
            val chain = getChain(id.chainId)
            if (!chain.isEthereumChain) {
                connectionPool.getConnectionOrNull(id.chainId)?.socketService?.switchUrl(id.nodeUrl)?.let {
                    scope.launch {
                        chainsRepository.notifyNodeSwitched(id.chainId, id.nodeUrl)
                    }
                }
            }
        }
    }

    suspend fun addNode(chainId: ChainId, nodeName: String, nodeUrl: String) =
        chainDao.insertChainNode(
            ChainNodeLocal(
                chainId,
                nodeUrl,
                nodeName,
                isActive = false,
                isDefault = false
            )
        )

    suspend fun deleteNode(id: NodeId) = chainDao.deleteNode(id.chainId, id.nodeUrl)

    suspend fun getNode(id: NodeId) =
        mapNodeLocalToNode(chainDao.getNode(id.chainId, id.nodeUrl))

    suspend fun updateNode(id: NodeId, name: String, url: String) =
        chainDao.updateNode(id.chainId, id.nodeUrl, name, url)

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

