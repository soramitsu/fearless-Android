package jp.co.soramitsu.runtime.multiNetwork

import android.util.Log
import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.compose.component.NetworkIssueType
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.mapNodeLocalToNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

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
    private val updatesMixin: UpdatesMixin,
    private val networkStateMixin: NetworkStateMixin,
    private val ethereumConnectionPool: EthereumConnectionPool
) : IChainRegistry, CoroutineScope by CoroutineScope(Dispatchers.Default),
    UpdatesProviderUi by updatesMixin {

    val syncedChains = MutableSharedFlow<List<Chain>>()

    val currentChains = syncedChains
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    val chainsById = currentChains.map { chains -> chains.associateBy { it.id } }
        .inBackground()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    init {
        syncUp()
    }

    fun syncUp() {
        launch {
            runCatching {
                chainSyncService.syncUp()
                runtimeSyncService.syncTypes()
            }
            chainDao.joinChainInfoFlow().mapList(::mapChainLocalToChain).diffed()
                .collect { (removed, addedOrModified, all) ->
                    val s = supervisorScope {
                        runCatching {
                            removed.forEach {
                                val chainId = it.id
                                if (it.isEthereumChain) {
                                    ethereumConnectionPool.stop(chainId)
                                    return@forEach
                                }
                                runtimeProviderPool.removeRuntimeProvider(chainId)
                                runtimeSubscriptionPool.removeSubscription(chainId)
                                runtimeSyncService.unregisterChain(chainId)
                                connectionPool.removeConnection(chainId)
                            }
                            updatesMixin.startChainsSyncUp(addedOrModified.filter { it.nodes.isNotEmpty() }
                                .map { it.id })
                            addedOrModified
                                .filter { /*it.disabled*/ it.nodes.isNotEmpty() }
                                .map { chain ->
                                    launch chainLaunch@{
                                        if (chain.isEthereumChain) {
                                            runCatching {
                                                ethereumConnectionPool.setupConnection(
                                                    chain,
                                                    onSelectedNodeChange = { chainId, newNodeUrl ->
                                                        notifyNodeSwitched(NodeId(chainId to newNodeUrl))
                                                    })
                                            }
                                            return@chainLaunch
                                        }

                                        runCatching {
                                            val connection = connectionPool.setupConnection(
                                                chain,
                                                onSelectedNodeChange = { chainId, newNodeUrl ->
                                                    notifyNodeSwitched(NodeId(chainId to newNodeUrl))
                                                }
                                            )
                                            runtimeSubscriptionPool.setupRuntimeSubscription(
                                                chain,
                                                connection
                                            )
                                            runtimeSyncService.registerChain(chain)
                                            runtimeProviderPool.setupRuntimeProvider(chain)
                                        }.onFailure { networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue()) }
                                            .onSuccess {
                                                networkStateMixin.notifyChainSyncSuccess(
                                                    chain.id
                                                )
                                            }
                                    }
                                }
                        }
                    }.onFailure {
                        Log.e(
                            "ChainRegistry",
                            "error while sync in chain registry $it"
                        )
                    }.requireValue()
                    joinAll(*s.toTypedArray())
                    this@ChainRegistry.syncedChains.emit(all)
                }
        }
    }

    override fun getConnection(chainId: String) = connectionPool.getConnection(chainId)

    @Deprecated(
        "Since we have ethereum chains, which don't have runtime, we must use the function with nullable return value",
        ReplaceWith("getRuntimeOrNull(chainId)")
    )
    override suspend fun getRuntime(chainId: ChainId): RuntimeSnapshot {
        return getRuntimeProvider(chainId).get()
    }

    suspend fun getRuntimeOrNull(chainId: ChainId): RuntimeSnapshot? {
        return kotlin.runCatching { getRuntimeProvider(chainId).getOrNullWithTimeout() }.getOrNull()
    }

    fun getConnectionOrNull(chainId: String) = connectionPool.getConnectionOrNull(chainId)

    suspend fun getRuntimeProvider(chainId: String): RuntimeProvider {
        return runtimeProviderPool.getRuntimeProvider(chainId)
    }

    suspend fun getRuntimeProviderOrNull(chainId: String): RuntimeProvider? {
        return runtimeProviderPool.getRuntimeProviderOrNull(chainId)
    }

    fun getAsset(chainId: ChainId, chainAssetId: String) =
        chainsById.replayCache.lastOrNull()?.get(chainId)?.assets?.firstOrNull {
            it.id == chainAssetId
        }

    override suspend fun getChain(chainId: ChainId): Chain {
        return chainsById.first().getValue(chainId)
    }

    override suspend fun getChains(): List<IChain> {
        return chainsById.first().values.toList()
    }

    fun nodesFlow(chainId: String) = chainDao.nodesFlow(chainId)
        .mapList(::mapNodeLocalToNode)

    suspend fun switchNode(id: NodeId) {
        withContext(Dispatchers.Default) {
            val chain = getChain(id.chainId)
            if (!chain.isEthereumChain) {
                connectionPool.getConnection(id.chainId).socketService.switchUrl(id.nodeUrl)
                notifyNodeSwitched(id)
            }
        }
    }

    private fun notifyNodeSwitched(id: NodeId) {
        launch(Dispatchers.IO) {
            chainDao.selectNode(id.chainId, id.nodeUrl)
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

    suspend fun getNode(id: NodeId) = mapNodeLocalToNode(chainDao.getNode(id.chainId, id.nodeUrl))

    suspend fun updateNode(id: NodeId, name: String, url: String) =
        chainDao.updateNode(id.chainId, id.nodeUrl, name, url)

    suspend fun getRemoteRuntimeVersion(chainId: ChainId): Int? {
        return chainDao.runtimeInfo(chainId)?.remoteVersion
    }
}

suspend fun ChainRegistry.getChain(chainId: ChainId): Chain {
    return getChain(chainId)
}

suspend fun ChainRegistry.chainWithAsset(chainId: ChainId, assetId: String): Pair<Chain, Asset> {
    val chain = chainsById.first().getValue(chainId)

    return chain to chain.assetsById.getValue(assetId)
}

suspend fun ChainRegistry.getRuntime(chainId: ChainId): RuntimeSnapshot {
    return getRuntimeProvider(chainId).get()
}

suspend fun ChainRegistry.getRuntimeOrNull(chainId: ChainId): RuntimeSnapshot? {
    return getRuntimeProviderOrNull(chainId)?.getOrNull()
}

suspend fun ChainRegistry.getRuntimeCatching(chainId: ChainId): Result<RuntimeSnapshot> {
    val providerResult = kotlin.runCatching { getRuntimeProvider(chainId) }

    return if (providerResult.isFailure) {
        Result.failure(providerResult.requireException())
    } else {
        kotlin.runCatching { providerResult.requireValue().get() }
    }
}

fun ChainRegistry.getSocket(chainId: ChainId) = getConnection(chainId).socketService
fun ChainRegistry.getSocketOrNull(chainId: ChainId) = getConnectionOrNull(chainId)?.socketService

suspend fun ChainRegistry.getService(chainId: ChainId): ChainService {
    return ChainService(
        runtimeProvider = getRuntimeProvider(chainId),
        connection = getConnection(chainId)
    )
}

fun Chain.toSyncIssue(): NetworkIssueItemState {
    return NetworkIssueItemState(
        iconUrl = this.icon,
        title = this.name,
        type = when {
            this.nodes.size > 1 -> NetworkIssueType.Node
            else -> NetworkIssueType.Network
        },
        chainId = this.id,
        chainName = this.name,
        assetId = this.utilityAsset?.id.orEmpty()
    )
}
