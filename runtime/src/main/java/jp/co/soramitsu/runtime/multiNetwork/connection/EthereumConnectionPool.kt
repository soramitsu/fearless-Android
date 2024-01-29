package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.requireValue
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
import jp.co.soramitsu.runtime.multiNetwork.toSyncIssue
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketClient
import org.web3j.protocol.websocket.WebSocketService

class EthereumConnectionPool(
    private val networkStateMixin: NetworkStateMixin,
    private val socketFactory: EthereumWebSocketFactory,
    private val nodesSettingsStorage: NodesSettingsStorage
) {
    private val pool = ConcurrentHashMap<ChainId, EthereumWebSocketConnection>()

    fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ) {
        val setupResult = kotlin.runCatching {
            require(chain.isEthereumChain)

            if (chain.nodes.isEmpty()) {
                throw IllegalStateException("There are no nodes for chain ${chain.name}")
            }

            val connection =
                EthereumWebSocketConnection(
                    chain,
                    socketFactory,
                    autoBalanceEnabled = {
                        nodesSettingsStorage.getIsAutoSelectNodes(chain.id)
                    },
                    onSelectedNodeChange = onSelectedNodeChange
                )
            pool[chain.id] = connection
            connection.connect().onFailure {
                Log.d(
                    "&&&",
                    "failed to connect to chain ${chain.name}, error: $it, ${it.message}"
                )
            }
        }

        if (setupResult.isFailure) {
            networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
            return
        }

        if (setupResult.requireValue().isFailure) {
            networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
            return
        }

        networkStateMixin.notifyChainSyncSuccess(chain.id)
    }

    fun get(chainId: String): EthereumWebSocketConnection? {
        return pool.getOrDefault(chainId, null)
            .apply { this ?: Log.d("&&&", "don't have a connection for chainOd: $chainId") }
    }

    fun stop(chainId: String) {
        pool.getOrDefault(chainId, null)?.let {
            it.web3j?.shutdown()
            pool.remove(chainId)
        }
    }
}

class EthereumWebSocketConnection(
    val chain: Chain,
    private val socketFactory: EthereumWebSocketFactory,
    private val autoBalanceEnabled: () -> Boolean,
    private val onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
) {

    companion object {
        private const val BLAST_NODE_KEYWORD = "blastapi"
        private const val WSS_NODE_PREFIX = "wss"
    }

    private val nodes: List<ChainNode> = formatWithApiKeys(chain)

    private var _web3j: Web3j? = null
    val web3j
        get() = _web3j

    private var _socket: WebSocketClient? = null

    private var _service: Web3jService? = null
    val service: Web3jService?
        get() {
            if (_service is WebSocketService && _socket != null && _socket?.isOpen == false) {
                Log.d("&&&", "service problems, connecting()")
                connect()
            }
            return _service
        }

    val switchRelay = NodesSwitchRelay(nodes)

    fun connect(): Result<ChainNode> {
        return if (autoBalanceEnabled()) {
            switchRelay { node ->
                connectInternal(node)
            }
        } else {
            val firstNode = nodes.first()
            connectInternal(firstNode)
        }.onSuccess {
            val url = if (it.url.contains(BLAST_NODE_KEYWORD)) {
                it.url.removeSuffix(blastApiKeys[chain.id].orEmpty())
            } else {
                it.url
            }
            onSelectedNodeChange(chain.id, url)
        }
    }

    private fun connectInternal(node: ChainNode): Result<ChainNode> {
        _web3j?.shutdown()

        when {
            node.url.startsWith("wss") -> {
                _socket = WebSocketClient(URI(node.url))

                val wsService = WebSocketService(_socket, false)
                runCatching { wsService.connect() }.onSuccess { _service = wsService }
                    .onFailure { return Result.failure("Node ${node.name} : ${node.url} connect failed $it") }
            }

            node.url.startsWith("http") -> {
                _service = HttpService(node.url, false)
            }

            else -> return Result.failure("Wrong node ${node.url}")
        }

        _web3j = Web3j.build(_service)

        val response = kotlin.runCatching { _web3j?.ethBlockNumber()?.send() }
        return if (response.isFailure) {
            Result.failure("Node ${node.name} : ${node.url} connect failed ${response.exceptionOrNull()}")
        } else {
            Result.success(node)
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

    fun switchNode(nodeUrl: String): Result<Any> {
        val url = if (nodeUrl.contains(BLAST_NODE_KEYWORD)) {
            nodeUrl.removeSuffix(blastApiKeys[chain.id].orEmpty())
        } else {
            nodeUrl
        }
        val node = nodes.firstOrNull { it.url == url || it.url.contains(url) } ?: return Result.failure("")
        return connectInternal(node)
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

class EthereumWebSocketFactory {
    companion object {
        private const val WSS_NODE_PREFIX = "wss"
        private const val HTTP_NODE_PREFIX = "http"
    }

    fun create(url: String): Web3jService {
        return when {
            url.startsWith(WSS_NODE_PREFIX) -> {
                val client = WebSocketClient(URI(url))
                WebSocketService(client, false)
            }

            url.startsWith(HTTP_NODE_PREFIX) -> {
                HttpService(url, false)
            }

            else -> throw IllegalArgumentException("Failed to create Ethereum network service: illegal node url: $url")
        }
    }
}