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

private const val EVM_CONNECTION_TAG = "EVM Connection"

class EthereumConnectionPool(
    private val networkStateMixin: NetworkStateMixin,
    private val nodesSettingsStorage: NodesSettingsStorage
) {
    private val pool = ConcurrentHashMap<ChainId, EthereumWebSocketConnection>()

    fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ) {
        val setupResult = kotlin.runCatching {
            EthereumWebSocketConnection(
                chain,
                autoBalanceEnabled = {
                    nodesSettingsStorage.getIsAutoSelectNodes(chain.id)
                },
                onSelectedNodeChange = onSelectedNodeChange
            )
        }

        if (setupResult.isFailure) {
            Log.d(
                EVM_CONNECTION_TAG,
                "create EthereumWebSocketConnection failed for chain ${chain.name}, error: ${setupResult.exceptionOrNull()} ${setupResult.exceptionOrNull()?.message}"
            )
            networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
            return
        }

        pool[chain.id] = setupResult.requireValue()

        val connectionResult = pool[chain.id]?.connect()

        if (connectionResult?.isFailure == true) {
            Log.d(
                EVM_CONNECTION_TAG,
                "create EVMConnection failed for chain ${chain.name}, error: ${connectionResult.exceptionOrNull()} ${connectionResult.exceptionOrNull()?.message}"
            )
            networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
            return
        }
        Log.d(EVM_CONNECTION_TAG, "setup connection successfully for ${chain.name}")
        networkStateMixin.notifyChainSyncSuccess(chain.id)
    }

    fun get(chainId: String): EthereumWebSocketConnection? {
        return pool.getOrDefault(chainId, null)
            .apply {
                this ?: Log.d(
                    EVM_CONNECTION_TAG,
                    "don't have a connection for chainOd: $chainId"
                )
            }
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
    private val autoBalanceEnabled: () -> Boolean,
    private val onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
) {

    companion object {
        private const val BLAST_NODE_KEYWORD = "blastapi"
        private const val WSS_NODE_PREFIX = "wss"
    }

    init {
        require(chain.isEthereumChain)
        check(chain.nodes.isNotEmpty()) { "There are no nodes for chain ${chain.name}" }
    }

    private val nodes: List<ChainNode> = formatWithApiKeys(chain)

    private var connection: EVMConnection? = null
    val web3j get() = connection?.web3j
    val service: Web3jService? get() = connection?.service

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
        }.onFailure { Log.e(EVM_CONNECTION_TAG, "failed to connect to chain ${chain.name}") }
    }

    private fun connectInternal(node: ChainNode): Result<ChainNode> {
        if (connection != null && connection is EVMConnection.WSS && (connection as EVMConnection.WSS).socket.isOpen) {
            connection?.web3j?.shutdown()
        }

        when {
            node.url.startsWith("wss") -> {
                connection = EVMConnection.WSS(node.url)

                runCatching { (connection as? EVMConnection.WSS)?.connect() }
                    .onFailure { return Result.failure("Node ${node.name} : ${node.url} connect failed $it") }
            }

            node.url.startsWith("http") -> {
                connection = EVMConnection.HTTP(node.url)
            }

            else -> return Result.failure("Wrong node ${node.url}")
        }


        val response = kotlin.runCatching { web3j?.ethBlockNumber()?.send() }
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
        val node =
            nodes.firstOrNull { it.url == url || it.url.contains(url) } ?: return Result.failure("")
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

sealed class EVMConnection(val url: String, val service: Web3jService) {

    private val _web3j: Web3j = Web3j.build(service)
    open val web3j: Web3j
        get() {
            return _web3j
        }

    class HTTP(url: String) : EVMConnection(url, HttpService(url, false))
    class WSS(url: String, val socket: WebSocketClient = WebSocketClient(URI(url))) :
        EVMConnection(url, WebSocketService(socket, false)) {
        fun connect() {
            (service as WebSocketService).connect()
        }

        override val web3j: Web3j
            get() {
                return super.web3j
            }
    }
}