package jp.co.soramitsu.runtime.multiNetwork

import javax.inject.Inject
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.mapNodeLocalToNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

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
    private val updatesMixin: UpdatesMixin
) : IChainRegistry, CoroutineScope by CoroutineScope(Dispatchers.Default), UpdatesProviderUi by updatesMixin {

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
                    removed.forEach {
                        val chainId = it.id
                        runtimeProviderPool.removeRuntimeProvider(chainId)
                        runtimeSubscriptionPool.removeSubscription(chainId)
                        runtimeSyncService.unregisterChain(chainId)
                        connectionPool.removeConnection(chainId)
                    }
                    updatesMixin.startChainsSyncUp(addedOrModified.filter { it.nodes.isNotEmpty() }.map { it.id })
                    addedOrModified.filter { /*it.disabled*/ it.nodes.isNotEmpty() }.forEach { chain ->
                        val connection = connectionPool.setupConnection(
                            chain,
                            onSelectedNodeChange = { chainId, newNodeUrl ->
                                launch { notifyNodeSwitched(NodeId(chainId to newNodeUrl)) }
                            }
                        )
                        runtimeSubscriptionPool.setupRuntimeSubscription(chain, connection)
                        runtimeSyncService.registerChain(chain)
                        runtimeProviderPool.setupRuntimeProvider(chain)
                    }
                    this@ChainRegistry.syncedChains.emit(all)
                }
        }
    }

    override fun getConnection(chainId: String) = connectionPool.getConnection(chainId)

    override suspend fun getRuntime(chainId: ChainId): RuntimeSnapshot {
        return getRuntimeProvider(chainId).get()
    }

    fun getConnectionOrNull(chainId: String) = connectionPool.getConnectionOrNull(chainId)

    fun getRuntimeProvider(chainId: String): RuntimeProvider {
        return runtimeProviderPool.getRuntimeProvider(chainId)
    }

    fun getAsset(chainId: ChainId, chainAssetId: String) = chainsById.replayCache.lastOrNull()?.get(chainId)?.assets?.firstOrNull {
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
        connectionPool.getConnection(id.chainId).socketService.switchUrl(id.nodeUrl)
        notifyNodeSwitched(id)
    }

    private suspend fun notifyNodeSwitched(id: NodeId) {
        chainDao.selectNode(id.chainId, id.nodeUrl)
    }

    suspend fun addNode(chainId: ChainId, nodeName: String, nodeUrl: String) =
        chainDao.insertChainNode(ChainNodeLocal(chainId, nodeUrl, nodeName, isActive = false, isDefault = false))

    suspend fun deleteNode(id: NodeId) = chainDao.deleteNode(id.chainId, id.nodeUrl)

    suspend fun getNode(id: NodeId) = mapNodeLocalToNode(chainDao.getNode(id.chainId, id.nodeUrl))

    suspend fun updateNode(id: NodeId, name: String, url: String) = chainDao.updateNode(id.chainId, id.nodeUrl, name, url)

    suspend fun getRemoteRuntimeVersion(chainId: ChainId): Int? {
        return chainDao.runtimeInfo(chainId)?.remoteVersion
    }
}

suspend fun ChainRegistry.getChain(chainId: ChainId): Chain {
    return getChain(chainId) as Chain
}

suspend fun ChainRegistry.chainWithAsset(chainId: ChainId, assetId: String): Pair<Chain, Asset> {
    val chain = chainsById.first().getValue(chainId)

    return chain to chain.assetsById.getValue(assetId)
}

suspend fun ChainRegistry.getRuntime(chainId: ChainId): RuntimeSnapshot {
    return getRuntimeProvider(chainId).get()
}

fun ChainRegistry.getSocket(chainId: ChainId) = getConnection(chainId).socketService
fun ChainRegistry.getSocketOrNull(chainId: ChainId) = getConnectionOrNull(chainId)?.socketService

fun ChainRegistry.getService(chainId: ChainId) = ChainService(
    runtimeProvider = getRuntimeProvider(chainId),
    connection = getConnection(chainId)
)
