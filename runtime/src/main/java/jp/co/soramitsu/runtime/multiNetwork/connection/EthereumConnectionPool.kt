package jp.co.soramitsu.runtime.multiNetwork.connection

import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.toSyncIssue
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import org.web3j.protocol.Web3j
import org.web3j.protocol.websocket.WebSocketService

class EthereumConnectionPool(
    private val networkStateMixin: NetworkStateMixin,
    private val socketFactory: EthereumWebSocketFactory,
    private val nodesSettingsStorage: NodesSettingsStorage,
) {

    companion object {
        private const val SOCKET_URL_PREFIX = "wss"
    }

    private val pool = ConcurrentHashMap<ChainId, EthereumWebSocketConnection>()

    suspend fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ) {
        val setupResult = kotlin.runCatching {
            require(chain.isEthereumChain)

            val wsNodes = chain.nodes.filter { it.url.startsWith(SOCKET_URL_PREFIX) }

            if (wsNodes.isEmpty()) {
                throw MissingWssNodesException(chain.name, chain.id)
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
            connection.connect()
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
    }

    fun stop(chainId: String) {
        pool.getOrDefault(chainId, null)?.let {
            it.web3j?.shutdown()
            pool.remove(chainId)
        }
    }
}

class MissingWssNodesException(chainName: String, chainId: String) :
    RuntimeException("There are no wss nodes in chain $chainName, chainId: $chainId")

class EthereumWebSocketConnection(
    private val chain: Chain,
    private val socketFactory: EthereumWebSocketFactory,
    private val autoBalanceEnabled: () -> Boolean,
    private val onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
) {

    companion object {
        private const val SOCKET_URL_PREFIX = "wss"
        private const val BLAST_NODE_KEYWORD = "blastapi"
    }

    private val wsNodes: List<ChainNode> = formatWithApiKeys(chain)

    private var _web3j: Web3j? = null
    val web3j
        get() = _web3j

    private var _service: WebSocketService? = null
    val service
        get() = _service

    val switchRelay = NodesSwitchRelay(wsNodes)

    init {
        if (wsNodes.isEmpty()) {
            throw MissingWssNodesException(chain.name, chain.id)
        }
    }

    suspend fun connect(): Result<Any> {
        return if (autoBalanceEnabled()) {
            switchRelay { node ->
                connectInternal(node)
            }
        } else {
            val firstNode = wsNodes.first()
            connectInternal(firstNode)
        }.onSuccess { onSelectedNodeChange(chain.id, it.url) }
    }

    private fun connectInternal(node: ChainNode): Result<ChainNode> {
        _web3j?.shutdown()
        _service = socketFactory.create(node.url)

        try {
            _service!!.connect({
                hashCode()
            }, {
                hashCode()
            }, {

            })
        } catch (exception: ConnectException) {
            hashCode()
            return Result.failure("Node ${node.name} : ${node.url} connect failed $exception")
        }

        _web3j = Web3j.build(_service)

        val response = kotlin.runCatching { _web3j?.ethBlockNumber()?.send() }
        return if (response.isFailure) {
            Result.failure("Node ${node.name} : ${node.url} connect failed $response.exceptionOrNull()")
        } else {
            Result.success(node)
        }
    }

    private fun formatWithApiKeys(chain: Chain): List<ChainNode> {
        return chain.nodes.filter { it.url.startsWith(SOCKET_URL_PREFIX) }.map {
            if (it.url.contains(BLAST_NODE_KEYWORD)) {
                it.copy(url = it.url.plus(blastApiKeys[chain.id].orEmpty()))
            } else {
                it
            }
        }
    }

    fun switchNode(nodeUrl: String) {
        val node = wsNodes.firstOrNull { it.url == nodeUrl || it.url.contains(nodeUrl) }
        node?.let { connectInternal(it) }
    }
}

private val blastApiKeys = mapOf(
    "1" to BuildConfig.BLAST_API_ETHEREUM_KEY,
    "38" to BuildConfig.BLAST_API_BSC_KEY,
    "11155111" to BuildConfig.BLAST_API_SEPOLIA_KEY,
    "5" to BuildConfig.BLAST_API_GOERLI_KEY,
    "unknown" to BuildConfig.BLAST_API_POLYGON_KEY
)
//wss://eth-mainnet.blastapi.io/f69cfb36-090a-4e29-ab21-95aeda2027a7

class EthereumWebSocketFactory {
    fun create(url: String) = WebSocketService(url, false)
}