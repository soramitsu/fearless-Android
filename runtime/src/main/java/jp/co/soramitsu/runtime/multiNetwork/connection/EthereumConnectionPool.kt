package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.utils.cycle
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import org.web3j.protocol.Web3j
import org.web3j.protocol.websocket.WebSocketService

class EthereumConnectionPool {

    companion object {
        private const val SOCKET_URL_PREFIX = "wss"
    }

    private val pool = ConcurrentHashMap<ChainId, EthereumWebSocketConnection>()

    fun setupConnection(chain: Chain) = kotlin.runCatching {
        require(chain.isEthereumChain)

        val wsNodes = chain.nodes.filter { it.url.startsWith(SOCKET_URL_PREFIX) }

        if (wsNodes.isEmpty()) {
            throw MissingWssNodesException(chain.name, chain.id)
        }

        val connection = EthereumWebSocketConnection(chain)
        pool[chain.id] = connection
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

class EthereumWebSocketConnection(private val chain: Chain) {

    companion object {
        private const val SOCKET_URL_PREFIX = "wss"
    }

    private val wsNodes: List<ChainNode> =
        chain.nodes.asSequence().filter { it.url.startsWith(SOCKET_URL_PREFIX) }.toList().map {
            if (it.url.contains("blastapi")) {
                it.copy(url = it.url.plus("ff5c9e1b-c5c8-4861-951c-d59ce8f5b22f"))
            } else it
        }

    private val availableNodesCycle = wsNodes.cycle().iterator()


    private var _web3j: Web3j? = null
    val web3j = _web3j

    private var _service: WebSocketService? = null
    val service = _service

    init {
        if (wsNodes.isEmpty()) {
            throw MissingWssNodesException(chain.name, chain.id)
        }

        connect(availableNodesCycle.next())
    }

    private fun connect(node: ChainNode) {
        _service = WebSocketService(node.url, false)

        try {
            _service!!.connect({

            }, {
                Log.d("&&&", "${chain.name} connection got error $it")
            }, {

            })
        } catch (exception: ConnectException) {
            Log.d("&&&", "${chain.name} service connect exception node ${node.url} $exception")
            tryNextNode()
        }
        _web3j?.shutdown()
        _web3j = Web3j.build(service)
        // test node
        val response = kotlin.runCatching { _web3j!!.ethBlockNumber().send() }
        if (response.isFailure) {
            Log.d("&&&", "${chain.name} node testing failure ${response.exceptionOrNull()}")
            tryNextNode()
        }
    }

    private fun tryNextNode() {
        val nextNode = availableNodesCycle.next()
        connect(nextNode)
    }
}