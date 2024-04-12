package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.cycle
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCTestnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.goerliChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonTestnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.sepoliaChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.toSyncIssue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketClient
import org.web3j.protocol.websocket.WebSocketService

private const val EVM_CONNECTION_TAG = "EVM Connection"

class EthereumConnectionPool(
    private val networkStateMixin: NetworkStateMixin,
) {
    private val poolStateFlow =
        MutableStateFlow<MutableMap<String, EthereumChainConnection>>(mutableMapOf())

    suspend fun await(chainId: String): EthereumChainConnection {
        return poolStateFlow.map { it.getOrDefault(chainId, null) }.first { it != null }.cast()
    }

    fun getOrNull(chainId: String): EthereumChainConnection? {
        return poolStateFlow.value.getOrDefault(chainId, null)
    }

    fun setupConnection(chain: Chain, onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit): EthereumChainConnection {
        if (poolStateFlow.value.containsKey(chain.id)) {
            return poolStateFlow.value.getValue(chain.id)
        } else {
            poolStateFlow.update { prev ->
                prev.also {
                    it[chain.id] = EthereumChainConnection(
                        chain,
                        onSelectedNodeChange = onSelectedNodeChange
                    ) { networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue()) }
                }
            }
            return poolStateFlow.value.getValue(chain.id)
        }
    }

    fun stop(chainId: String) {
        poolStateFlow.update { prev ->
            prev.also {
                it.remove(chainId)?.apply { web3j?.shutdown() }
            }
        }
    }
}

class EthereumChainConnection(
    val chain: Chain,
    private val onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit,
    private val allNodesHaveFailed: () -> Unit
) {
    private val scope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(
                EVM_CONNECTION_TAG,
                "${chain.name} connection error: $throwable"
            )
        })

    companion object {
        private const val BLAST_NODE_KEYWORD = "blastapi"
        private const val WSS_NODE_PREFIX = "wss"
    }


    private val nodes: List<ChainNode> = formatWithApiKeys(chain)
    private var nodesCycle = nodes.cycle().iterator()

    private var connection: EVMConnection? = null
    val web3j get() = connection?.web3j
    val service: Web3jService? get() = connection?.service

    val statusFlow = MutableStateFlow<EvmConnectionStatus?>(null)

    private val nodesAttempts = ConcurrentHashMap<String, Int>().also {
        nodes.forEach { node ->
            it[node.url] = 0
        }
    }

    init {
        require(chain.isEthereumChain)
        check(chain.nodes.isNotEmpty()) { "There are no nodes for chain ${chain.name}" }

        statusFlow.onEach {
            when (it) {
                is EvmConnectionStatus.Connected -> {
                    val url = if (it.node.contains(BLAST_NODE_KEYWORD)) {
                        it.node.removeSuffix(blastApiKeys[chain.id].orEmpty())
                    } else {
                        it.node
                    }
                    onSelectedNodeChange(chain.id, url)
                }

                is EvmConnectionStatus.Error,
                is EvmConnectionStatus.Closed,
                null -> {
                    connectNextNode()
                }

                else -> Unit
            }
        }.launchIn(scope)
    }

    private fun connectNextNode() {
        scope.launch {
            supervisorScope {
                if (nodesAttempts.all { it.value >= 5 }) {
                    allNodesHaveFailed()
                    return@supervisorScope
                }
                val nextNode = nodesCycle.next()

                if (nodesAttempts.getOrDefault(nextNode.url, 0) >= 5) {
                    statusFlow.update { null }
                    return@supervisorScope
                }

                if (connection != null && connection is EVMConnection.WSS && (connection as EVMConnection.WSS).socket.isOpen) {
                    connection?.web3j?.shutdown()
                }

                when {
                    nextNode.url.startsWith("wss") -> {
                        connection = EVMConnection.WSS(nextNode.url) { newStatus ->
                            statusFlow.update { prevStatus ->
                                if (newStatus is EvmConnectionStatus.Closed && prevStatus is EvmConnectionStatus.ClosedByApp) {
                                    EvmConnectionStatus.ClosedByApp
                                } else {
                                    newStatus
                                }
                            }
                        }
                        (connection as? EVMConnection.WSS)?.connect()
                    }

                    nextNode.url.startsWith("http") -> {
                        connection = EVMConnection.HTTP(nextNode.url) { newStatus ->
                            statusFlow.update { newStatus }
                        }
                    }

                    else -> {
                        statusFlow.update { null }
                        return@supervisorScope
                    }
                }

                nodesAttempts[nextNode.url] = (nodesAttempts[nextNode.url] ?: 0) + 1
            }
        }
    }

    private fun formatWithApiKeys(chain: Chain): List<ChainNode> {
        return chain.nodes.sortedByDescending { it.url.startsWith(WSS_NODE_PREFIX) }.map {
            if (it.url.contains(BLAST_NODE_KEYWORD)) {
                it.copy(url = it.url.plus(blastApiKeys[chain.id].orEmpty()))
            } else {
                it
            }
        }
    }
}

private val blastApiKeys = mapOf(
    ethereumChainId to BuildConfig.BLAST_API_ETHEREUM_KEY,
    BSCChainId to BuildConfig.BLAST_API_BSC_KEY,
    BSCTestnetChainId to BuildConfig.BLAST_API_BSC_KEY,
    sepoliaChainId to BuildConfig.BLAST_API_SEPOLIA_KEY,
    goerliChainId to BuildConfig.BLAST_API_GOERLI_KEY,
    polygonChainId to BuildConfig.BLAST_API_POLYGON_KEY,
    polygonTestnetChainId to BuildConfig.BLAST_API_POLYGON_KEY
)

sealed class EVMConnection(
    val url: String,
    val service: Web3jService,
    internal val onStatusChanged: (EvmConnectionStatus) -> Unit
) {
    private val _web3j: Web3j = Web3j.build(service)
    open val web3j: Web3j
        get() {
            return _web3j
        }

    class HTTP(url: String, onStatusChanged: (EvmConnectionStatus) -> Unit) :
        EVMConnection(url, HttpService(url, false), onStatusChanged) {
        init {
            onStatusChanged(EvmConnectionStatus.Connecting(url))
            runCatching { web3j.ethBlockNumber().send() }
                .onFailure {
                    onStatusChanged(EvmConnectionStatus.Error(url, it))
                }
                .onSuccess {
                    onStatusChanged(EvmConnectionStatus.Connected(url))
                }
        }
    }

    class WSS(
        url: String,
        val socket: WebSocketClient = WebSocketClient(URI(url)),
        onStatusChanged: (EvmConnectionStatus) -> Unit
    ) :
        EVMConnection(url, WebSocketService(socket, false), onStatusChanged) {
        fun connect() {
            onStatusChanged(EvmConnectionStatus.Connecting(url))
            runCatching {
                (service as WebSocketService).connect(
                    /* onMessage = */ {},
                    /* onError = */
                    {
                        onStatusChanged(EvmConnectionStatus.Error(url, it))
                    },
                    /* onClose = */
                    {
                        onStatusChanged(EvmConnectionStatus.Closed)
                    }
                )
            }.onFailure {
                onStatusChanged(EvmConnectionStatus.Error(url, it))
                return
            }
            runCatching { web3j.ethBlockNumber().send() }
                .onFailure { onStatusChanged(EvmConnectionStatus.Error(url, it)) }
                .onSuccess { onStatusChanged(EvmConnectionStatus.Connected(url)) }
        }

        override val web3j: Web3j
            get() {
                return super.web3j
            }
    }
}

sealed class EvmConnectionStatus {
    object Created : EvmConnectionStatus()
    data class Connecting(val node: String) : EvmConnectionStatus()
    data class Connected(val node: String) : EvmConnectionStatus()
    object Closed : EvmConnectionStatus()
    data class Error(val node: String, val error: Throwable) : EvmConnectionStatus()

    object ClosedByApp : EvmConnectionStatus()
}
