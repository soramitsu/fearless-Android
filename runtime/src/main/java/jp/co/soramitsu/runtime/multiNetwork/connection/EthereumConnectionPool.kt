package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.NetworkStateService
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketClient
import org.web3j.protocol.websocket.WebSocketService

private const val EVM_CONNECTION_TAG = "EVM Connection"

class EthereumConnectionPool(
    private val networkStateService: NetworkStateService,
) {
    private val poolStateFlow = MutableStateFlow<MutableMap<String, EthereumChainConnection>>(mutableMapOf())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun await(chainId: String): EthereumChainConnection {
        return poolStateFlow.map { it[chainId] }.filterNotNull().first()
    }

    fun getOrNull(chainId: String): EthereumChainConnection? {
        return poolStateFlow.value[chainId]
    }

    fun setupConnection(chain: Chain, onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit): EthereumChainConnection {
        val connection = poolStateFlow.updateAndGet { currentPool ->
            if (currentPool.containsKey(chain.id)) {
                currentPool
            } else {
                val newConnection = EthereumChainConnection(
                    chain,
                    onSelectedNodeChange = onSelectedNodeChange
                ) { networkStateService.notifyConnectionProblem(chain.id) }

                newConnection.statusFlow.onEach { status ->
                    if (status is EvmConnectionStatus.Connected) {
                        networkStateService.notifyConnectionSuccess(chain.id)
                    } else {
                        networkStateService.notifyConnectionProblem(chain.id)
                    }
                }.launchIn(scope)

                currentPool.toMutableMap().apply { put(chain.id, newConnection) }
            }
        }[chain.id]!!

        return connection
    }

    fun stop(chainId: String) {
        poolStateFlow.update { currentPool ->
            currentPool.toMutableMap().apply {
                remove(chainId)?.apply { web3j?.shutdown() }
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

    private val nodesAttempts = nodes.associate { it.url to AtomicInteger(0) }

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
                if (nodesAttempts.all { it.value.get() >= 5 }) {
                    allNodesHaveFailed()
                    return@supervisorScope
                }
                delay(1000) // delay between reconnects

                val nextNode = nodesCycle.next()

                if ((nodesAttempts[nextNode.url]?.get() ?: 0) >= 5) {
                    statusFlow.update { null }
                    return@supervisorScope
                }

                connection?.web3j?.shutdown() // shutdown previous connection

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

                nodesAttempts[nextNode.url]?.incrementAndGet()
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
        EVMConnection(
            url, HttpService(
                url, OkHttpClient.Builder()
                    .connectTimeout(60L, TimeUnit.SECONDS)
                    .writeTimeout(60L, TimeUnit.SECONDS)
                    .readTimeout(60L, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true).build()
            ), onStatusChanged
        ) {
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
