package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketClient
import org.web3j.protocol.websocket.WebSocketService
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ConnectionPool as HttpConnectionPool

private const val EVM_CONNECTION_TAG = "EVM Connection"

class EthereumConnectionPool(
    private val networkStateService: NetworkStateService,
) {
    private val poolStateFlow = MutableStateFlow<Map<String, EthereumChainConnection>>(emptyMap())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Serializes setup, replacement and stop, so a chain never has two live connections and the last call wins.
    // statusJobs is used only under it. Connections are closed after it is released.
    private val lock = Any()
    private val statusJobs = mutableMapOf<String, Job>()

    suspend fun await(chainId: String): EthereumChainConnection {
        return poolStateFlow.map { it[chainId] }.filterNotNull().first()
    }

    fun getOrNull(chainId: String): EthereumChainConnection? {
        return poolStateFlow.value[chainId]
    }

    /**
     * The chain's connection, created on first use.
     *
     * An existing connection is kept while the chain's effective ordered endpoints are unchanged, so node-selection
     * writes and catalog edits that leave the URLs alone never reconnect. A changed list replaces the connection in
     * one step: callers of [getOrNull] and [await] get the new one, and the old one is closed for good, transport
     * included, with its late callbacks ignored.
     *
     * Created under a lock and outside any retried state update: a connection starts connecting as it is built, so a
     * discarded duplicate would keep a socket of its own.
     */
    fun setupConnection(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ): EthereumChainConnection {
        var replaced: EthereumChainConnection? = null
        try {
            return synchronized(lock) {
                val existing = poolStateFlow.value[chain.id]
                if (existing != null && !existing.isClosed &&
                    existing.endpoints == EthereumChainConnection.effectiveEndpoints(chain)
                ) {
                    return existing
                }
                replaced = existing
                statusJobs.remove(chain.id)?.cancel()
                try {
                    create(chain, onSelectedNodeChange)
                } catch (error: Exception) {
                    // The refreshed chain cannot be served, and its retired endpoints must not stay in use either.
                    if (existing != null) poolStateFlow.value = poolStateFlow.value - chain.id
                    throw error
                }
            }
        } finally {
            replaced?.close()
        }
    }

    /** Removes the chain's connection and closes it for good: no reconnect, no further node attempts. */
    fun stop(chainId: String) {
        val removed = synchronized(lock) {
            statusJobs.remove(chainId)?.cancel()
            poolStateFlow.value[chainId]?.also { poolStateFlow.value = poolStateFlow.value - chainId }
        }
        removed?.close()
    }

    // Called with the pool lock held. Publish before starting the status collector.
    private fun create(
        chain: Chain,
        onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit
    ): EthereumChainConnection {
        var owner: EthereumChainConnection? = null
        fun whileCurrent(action: () -> Unit) {
            synchronized(lock) {
                if (owner != null && poolStateFlow.value[chain.id] === owner) action()
            }
        }
        val connection = EthereumChainConnection(
            chain,
            onSelectedNodeChange = { chainId, url -> whileCurrent { onSelectedNodeChange(chainId, url) } }
        ) { whileCurrent { networkStateService.notifyConnectionProblem(chain.id) } }

        owner = connection

        // Published before its watcher starts, so the watcher reports the first status as before.
        poolStateFlow.value = poolStateFlow.value + (chain.id to connection)
        statusJobs[chain.id] = connection.statusFlow.onEach { status ->
            whileCurrent {
                if (status is EvmConnectionStatus.Connected) {
                    networkStateService.notifyConnectionSuccess(chain.id)
                } else {
                    networkStateService.notifyConnectionProblem(chain.id)
                }
            }
        }.launchIn(scope)
        return connection
    }
}

class EthereumChainConnection(
    val chain: Chain,
    private val onSelectedNodeChange: (chainId: ChainId, newNodeUrl: String) -> Unit,
    private val allNodesHaveFailed: () -> Unit
) {
    private val scope =
        CoroutineScope(
            Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(
                EVM_CONNECTION_TAG,
                "${chain.name} connection error: $throwable"
            )
        }
        )

    companion object {
        private const val BLAST_NODE_KEYWORD = "blastapi"
        private const val WSS_NODE_PREFIX = "wss"
        private const val HTTP_NODE_PREFIX = "http"
        private const val MAX_ATTEMPTS_PER_NODE = 5
        private const val RECONNECT_DELAY_MILLIS = 1000L

        /**
         * The ordered endpoints a connection to [chain] cycles through: WebSocket nodes first, Blast nodes with the
         * build's key. Node names and selection flags do not count.
         */
        internal fun effectiveEndpoints(chain: Chain): List<String> = formatWithApiKeys(chain).map { it.url }

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

    private val nodes: List<ChainNode> = formatWithApiKeys(chain)

    /** This connection's effective ordered endpoints. They never change: the pool replaces the connection instead. */
    internal val endpoints: List<String> = nodes.map { it.url }

    // Guards the node cycle, the newest attempt, the scheduled flag, the open transports and the closed flag. A
    // transport's status counts only while its attempt is the newest one of an open connection, so the Closed that
    // follows shutting a replaced socket down can never start another attempt.
    private val lock = Any()
    private val nodesCycle = nodes.cycle().iterator()
    private var newestAttempt: Any? = null
    private var attemptScheduled = false
    private val openTransports = mutableSetOf<EVMConnection>()

    @Volatile
    private var connection: EVMConnection? = null
    val web3j get() = connection?.web3j
    val service: Web3jService? get() = connection?.service

    @Volatile
    internal var isClosed = false
        private set

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
                    if (!isClosed) onSelectedNodeChange(chain.id, url)
                }
                // The first attempt. Later attempts follow the reporting transport's own Error or Closed.
                null -> connectNextNode()
                else -> Unit
            }
        }.launchIn(scope)
    }

    /**
     * Ends this connection for good: no further node attempts, its scope cancelled and every transport it opened
     * closed, HTTP and WebSocket alike. Shutting a client down alone closed its socket, and the Closed status then
     * started the next node attempt, so a stopped connection reconnected on its own, outside the pool.
     */
    fun close() {
        val transports = synchronized(lock) {
            if (isClosed) return
            isClosed = true
            newestAttempt = null
            connection = null
            statusFlow.value = EvmConnectionStatus.ClosedByApp
            openTransports.toList().also { openTransports.clear() }
        }
        scope.cancel()
        transports.forEach { it.close() }
    }

    private fun connectNextNode() {
        if (synchronized(lock) { scheduleAttemptLocked() }) launchAttempt()
    }

    private fun scheduleAttemptLocked(): Boolean {
        if (isClosed || attemptScheduled) return false
        attemptScheduled = true
        return true
    }

    private fun launchAttempt() {
        scope.launch {
            if (nodes.none { it.isUsable() }) {
                synchronized(lock) { attemptScheduled = false }
                if (!isClosed) allNodesHaveFailed()
                return@launch
            }
            delay(RECONNECT_DELAY_MILLIS) // delay between reconnects
            attemptNextNode()
        }
    }

    private fun attemptNextNode() {
        val attempt = Any()
        var previous: EVMConnection? = null
        val node = synchronized(lock) {
            attemptScheduled = false
            if (isClosed) return
            nextUsableNodeLocked()?.also {
                newestAttempt = attempt
                previous = connection?.also { openTransports.remove(it) }
            }
        }
        if (node == null) {
            if (!isClosed) allNodesHaveFailed()
            return
        }
        previous?.close() // shut down the previous transport; its late status is already ignored

        nodesAttempts[node.url]?.incrementAndGet()
        val transport = runCatching {
            if (node.url.startsWith(WSS_NODE_PREFIX)) {
                EVMConnection.WSS(node.url) { status -> onTransportStatus(attempt, status) }
            } else {
                EVMConnection.HTTP(node.url) { status -> onTransportStatus(attempt, status) }
            }
        }.getOrElse {
            // A malformed URL counts as a failed attempt instead of ending the retries.
            onTransportStatus(attempt, EvmConnectionStatus.Error(node.url, it))
            return
        }

        val adopted = synchronized(lock) {
            isCurrentLocked(attempt).also { current ->
                if (current) {
                    openTransports += transport
                    // A WebSocket client is handed out while it connects, as before; an HTTP client once probed.
                    if (transport is EVMConnection.WSS) connection = transport
                }
            }
        }
        if (!adopted) {
            transport.close()
            return
        }

        transport.connect()

        // close() or a newer attempt may have run while this one connected: its transport is closed, never handed out.
        val kept = synchronized(lock) {
            isCurrentLocked(attempt).also { current ->
                if (!current) {
                    openTransports.remove(transport)
                } else if (transport is EVMConnection.HTTP) {
                    connection = transport
                }
            }
        }
        if (!kept) transport.close()
    }

    private fun onTransportStatus(attempt: Any, status: EvmConnectionStatus) {
        val retry = synchronized(lock) {
            if (!isCurrentLocked(attempt)) return
            statusFlow.value = status
            (status is EvmConnectionStatus.Error || status is EvmConnectionStatus.Closed) && scheduleAttemptLocked()
        }
        if (retry) launchAttempt()
    }

    private fun isCurrentLocked(attempt: Any): Boolean = !isClosed && newestAttempt === attempt

    private fun nextUsableNodeLocked(): ChainNode? {
        repeat(nodes.size) {
            val candidate = nodesCycle.next()
            if (candidate.isUsable()) return candidate
        }
        return null
    }

    private fun ChainNode.isUsable(): Boolean {
        val supported = url.startsWith(WSS_NODE_PREFIX) || url.startsWith(HTTP_NODE_PREFIX)
        return supported && nodesAttempts.getValue(url).get() < MAX_ATTEMPTS_PER_NODE
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
    internal val onStatusChanged: (EvmConnectionStatus) -> Unit,
    private val closed: AtomicBoolean = AtomicBoolean(false)
) {
    val web3j: Web3j = Web3j.build(service)
    protected val isClosed: Boolean get() = closed.get()

    /** Opens the transport and probes the node, reporting Connecting and then Connected or Error. */
    abstract fun connect()

    /** Closes the transport for good: it sends nothing more, keeps no socket open and reports no further status. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { releaseTransport() }
        runCatching { web3j.shutdown() }
    }

    protected open fun releaseTransport() = Unit

    protected fun report(status: EvmConnectionStatus) {
        if (!closed.get()) onStatusChanged(status)
    }

    class HTTP private constructor(
        url: String,
        private val client: OkHttpClient,
        closed: AtomicBoolean,
        onStatusChanged: (EvmConnectionStatus) -> Unit
    ) : EVMConnection(url, HttpService(url, client), onStatusChanged, closed) {

        constructor(url: String, onStatusChanged: (EvmConnectionStatus) -> Unit) :
            this(url, AtomicBoolean(false), onStatusChanged)

        private constructor(url: String, closed: AtomicBoolean, onStatusChanged: (EvmConnectionStatus) -> Unit) :
            this(url, httpClient(closed), closed, onStatusChanged)

        override fun connect() {
            report(EvmConnectionStatus.Connecting(url))
            runCatching { web3j.ethBlockNumber().send() }
                .onFailure { report(EvmConnectionStatus.Error(url, it)) }
                .onSuccess { report(EvmConnectionStatus.Connected(url)) }
        }

        /**
         * HttpService.close() does nothing, so web3j's shutdown alone left this client reaching the old endpoint.
         * Once closed, new requests fail and idle sockets are closed. A call already in flight is left to finish,
         * so a request that was already sent, such as a transaction, gets its answer; its socket is closed when it ends.
         */
        override fun releaseTransport() {
            client.connectionPool.evictAll()
        }

        private companion object {
            const val TIMEOUT_SECONDS = 60L

            fun httpClient(closed: AtomicBoolean): OkHttpClient {
                val connections = HttpConnectionPool()
                return OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(connections)
                    // A client captured before the close must not reach a retired endpoint afterwards.
                    .addInterceptor { chain ->
                        if (closed.get()) throw IOException("EVM HTTP transport is closed")
                        chain.proceed(chain.request())
                    }
                    .eventListener(object : EventListener() {
                        override fun callEnd(call: Call) = evictIfClosed()
                        override fun callFailed(call: Call, ioe: IOException) = evictIfClosed()
                        private fun evictIfClosed() {
                            if (closed.get()) connections.evictAll()
                        }
                    })
                    .build()
            }
        }
    }

    class WSS(
        url: String,
        val socket: WebSocketClient = WebSocketClient(URI(url)),
        onStatusChanged: (EvmConnectionStatus) -> Unit
    ) :
        EVMConnection(url, WebSocketService(socket, false), onStatusChanged) {
        override fun connect() {
            report(EvmConnectionStatus.Connecting(url))
            if (isClosed) return
            runCatching {
                (service as WebSocketService).connect(
                    {},
                    { error -> report(EvmConnectionStatus.Error(url, error)) },
                    { report(EvmConnectionStatus.Closed) }
                )
            }.onFailure {
                report(EvmConnectionStatus.Error(url, it))
                return
            }
            // Closing before the socket starts its handshake is a no-op in Java-WebSocket.
            // A handshake that finishes after retirement must close before sending the probe.
            if (isClosed) {
                socket.close()
                return
            }
            runCatching { web3j.ethBlockNumber().send() }
                .onFailure { report(EvmConnectionStatus.Error(url, it)) }
                .onSuccess { report(EvmConnectionStatus.Connected(url)) }
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
