package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A message the relay delivered for a subscribed topic. */
internal class WcRelayMessage(
    val topic: String,
    val message: String,
    val publishedAt: Long?,
    val attestation: String?
)

/** The relay could not be reached or refused a call. Never carries message contents. */
internal class WcRelayException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class WcRelayConfig(
    /** The WalletConnect Cloud project id: 32 hex characters. */
    val projectId: String,
    /** Sent so the relay can match a project allowlist; may be null. */
    val packageName: String?,
    val userAgent: String,
    /** The first is preferred; after a failed connect the next one is tried, as the official SDKs fail over. */
    val relayUrls: List<String> = listOf(DEFAULT_RELAY_URL, FAILOVER_RELAY_URL),
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    /** How long the socket stays open after the app went to the background. */
    val backgroundGraceMillis: Long = DEFAULT_BACKGROUND_GRACE_MILLIS
) {
    val isValid: Boolean get() = WcEncoding.isHex(projectId, PROJECT_ID_BYTES) && relayUrls.isNotEmpty()

    companion object {
        const val DEFAULT_RELAY_URL = "wss://relay.walletconnect.org"
        const val FAILOVER_RELAY_URL = "wss://relay.walletconnect.com"
        private const val PROJECT_ID_BYTES = 16
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000L
        private const val DEFAULT_BACKGROUND_GRACE_MILLIS = 15_000L
    }
}

/**
 * JSON-RPC client of the WalletConnect relay (IRN): connects with a client-auth JWT, keeps every
 * watched topic subscribed across reconnects, publishes, acknowledges `irn_subscription` deliveries
 * and hands them to [onMessage] one at a time, in order. It stays connected only while topics are
 * watched and the app is in the foreground, or while a call is waiting for the relay.
 */
@Suppress("TooManyFunctions")
internal class WcRelayClient(
    private val transport: WcRelayTransport,
    private val config: WcRelayConfig,
    private val identity: suspend () -> WcClientIdentity,
    private val clock: WcClock,
    private val scope: CoroutineScope,
    private val onMessage: suspend (WcRelayMessage) -> Unit,
    private val backoffMillis: (attempt: Int) -> Long = ::defaultBackoff
) {
    private val ids = WcIdGenerator(clock)
    private val watched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val subscriptionIds = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val inbox = Channel<WcRelayMessage>(INBOX_CAPACITY)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val demand = AtomicInteger()
    private val current = MutableStateFlow<Connection?>(null)
    private val _connected = MutableStateFlow(false)

    @Volatile
    private var foreground = true

    @Volatile
    private var backgroundSince: Long? = null
    private var loop: Job? = null

    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun start() {
        if (loop != null) return
        loop = scope.launch { connectionLoop() }
        scope.launch {
            for (message in inbox) {
                runCatchingNonCancellation { onMessage(message) }
            }
        }
    }

    /**
     * Keeps [topics] subscribed on every connection from the next one on. Called before [start] for
     * the stored topics; [subscribe] subscribes a topic on the open connection at once.
     */
    fun watch(topics: Collection<String>) {
        watched.addAll(topics)
        wake.trySend(Unit)
    }

    fun isWatching(topic: String): Boolean = topic in watched

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        backgroundSince = if (isForeground) null else clock.nowMillis()
        wake.trySend(Unit)
    }

    /** Subscribes [topic] now, waiting for the relay, and keeps it subscribed on later connections. */
    suspend fun subscribe(topic: String) {
        watched += topic
        withConnection { connection ->
            subscribeOn(connection, listOf(topic))
            fetchMessages(connection, listOf(topic))
        }
    }

    suspend fun unsubscribe(topic: String) = unsubscribe(listOf(topic))

    /** Stops watching [topics]; one `irn_batchUnsubscribe` tells the relay when there are several. */
    suspend fun unsubscribe(topics: Collection<String>) {
        if (topics.isEmpty()) return
        watched.removeAll(topics.toSet())
        val subscriptions = topics.mapNotNull { topic -> subscriptionIds.remove(topic)?.let { id -> topic to id } }
        val connection = current.value
        if (connection != null && subscriptions.isNotEmpty()) {
            runCatchingNonCancellation {
                if (subscriptions.size == 1) {
                    relayRequest(connection, METHOD_UNSUBSCRIBE, subscriptionJson(subscriptions.single()))
                } else {
                    val batch = buildJsonArray { subscriptions.forEach { add(subscriptionJson(it)) } }
                    relayRequest(connection, METHOD_BATCH_UNSUBSCRIBE, buildJsonObject { put("subscriptions", batch) })
                }
            }
        }
        wake.trySend(Unit)
    }

    private fun subscriptionJson(subscription: Pair<String, String>) = buildJsonObject {
        put("topic", subscription.first)
        put("id", subscription.second)
    }

    /** Publishes an encoded [message] on [topic]; throws [WcRelayException] when the relay does not accept it. */
    suspend fun publish(
        topic: String,
        message: String,
        ttlSeconds: Long,
        tag: Int,
        prompt: Boolean = false
    ) {
        val params = buildJsonObject {
            put("topic", topic)
            put("message", message)
            put("ttl", ttlSeconds)
            put("tag", tag)
            put("prompt", prompt)
        }
        var lastError: WcRelayException? = null
        repeat(PUBLISH_ATTEMPTS) {
            try {
                val result = withConnection { connection -> relayRequest(connection, METHOD_PUBLISH, params) }
                if ((result as? JsonPrimitive)?.booleanOrNull == false) throw WcRelayException("The relay refused the message")
                return
            } catch (error: WcRelayException) {
                lastError = error
            }
        }
        throw lastError ?: WcRelayException("The relay refused the message")
    }

    private suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
        demand.incrementAndGet()
        wake.trySend(Unit)
        try {
            val connection = withTimeoutOrNull(config.connectTimeoutMillis * 2) { current.filterNotNull().first() }
                ?: throw WcRelayException("The relay is unreachable")
            return block(connection)
        } finally {
            demand.decrementAndGet()
            wake.trySend(Unit)
        }
    }

    private fun shouldConnect(): Boolean {
        if (demand.get() > 0) return true
        if (watched.isEmpty()) return false
        val since = backgroundSince
        return foreground || since != null && clock.nowMillis() - since < config.backgroundGraceMillis
    }

    private suspend fun connectionLoop() {
        var failures = 0
        var urlIndex = 0
        while (scope.isActive) {
            if (!shouldConnect()) {
                waitForChange()
                continue
            }
            val url = config.relayUrls[urlIndex % config.relayUrls.size]
            val outcome = runCatchingNonCancellation { open(url) }.getOrNull()?.let { serve(it) } ?: ConnectionOutcome.FAILED
            failures = if (outcome == ConnectionOutcome.STABLE) 0 else failures + 1
            // A relay that cannot be reached or refuses us is left for the next one.
            if (outcome == ConnectionOutcome.FAILED) urlIndex += 1
            if (failures > 0) waitForChange(backoffMillis(failures))
        }
    }

    // Serves one open connection until it closes or is no longer needed.
    private suspend fun serve(connection: Connection): ConnectionOutcome {
        val openedAt = clock.nowMillis()
        return try {
            subscribeOn(connection, watched.toList())
            current.value = connection
            _connected.value = true
            fetchMessages(connection, watched.toList())
            holdOpen(connection)
            // A relay that closes right after accepting us is retried with backoff, not in a loop.
            if (clock.nowMillis() - openedAt < STABLE_CONNECTION_MILLIS) ConnectionOutcome.UNSTABLE else ConnectionOutcome.STABLE
        } catch (ignored: WcRelayException) {
            ConnectionOutcome.FAILED
        } finally {
            current.value = null
            _connected.value = false
            connection.socket?.close()
            subscriptionIds.clear()
            failPending()
        }
    }

    // Waits until the connection closes or is no longer needed, then returns.
    private suspend fun holdOpen(connection: Connection) {
        while (scope.isActive) {
            if (!shouldConnect()) return
            // Re-checked periodically: the background grace period ends without any event.
            val recheck = minOf(BACKGROUND_RECHECK_MILLIS, config.backgroundGraceMillis).coerceAtLeast(MIN_RECHECK_MILLIS)
            val closed = withTimeoutOrNull(recheck) {
                select {
                    connection.closed.onAwait { true }
                    wake.onReceive { false }
                }
            } ?: false
            if (closed) return
        }
    }

    private suspend fun waitForChange(timeoutMillis: Long = BACKGROUND_RECHECK_MILLIS) {
        withTimeoutOrNull(timeoutMillis) { wake.receive() }
    }

    private suspend fun open(url: String): Connection {
        val clientIdentity = identity()
        val jwt = clientIdentity.signJwt(
            audience = url,
            subject = WcEncoding.hex(WcCrypto.randomBytes(WcCrypto.KEY_LENGTH)),
            issuedAtSeconds = clock.nowSeconds()
        )
        val query = buildList {
            add("auth" to jwt)
            add("projectId" to config.projectId)
            add("ua" to config.userAgent)
            add("useOnCloseEvent" to "true")
            config.packageName?.let { add("packageName" to it) }
        }.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, Charsets.UTF_8.name()) }
        val connection = Connection()
        transport.open("$url?$query", connection)
        try {
            withTimeout(config.connectTimeoutMillis) { connection.opened.await() }
        } catch (error: TimeoutCancellationException) {
            connection.socket?.close()
            throw WcRelayException("The relay did not answer", error)
        }
        return connection
    }

    private suspend fun subscribeOn(connection: Connection, topics: List<String>) {
        for (chunk in topics.chunked(BATCH_LIMIT)) {
            if (chunk.size == 1) {
                val result = relayRequest(connection, METHOD_SUBSCRIBE, buildJsonObject { put("topic", chunk.single()) })
                rememberSubscription(chunk.single(), (result as? JsonPrimitive)?.takeIf { it.isString }?.content)
            } else {
                val result = relayRequest(connection, METHOD_BATCH_SUBSCRIBE, topicsJson(chunk))
                val returnedIds = (result as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { id -> id.isString }?.content }
                chunk.forEachIndexed { index, topic -> rememberSubscription(topic, returnedIds?.getOrNull(index)) }
            }
        }
    }

    private fun topicsJson(topics: List<String>) = buildJsonObject {
        put("topics", buildJsonArray { topics.forEach { add(JsonPrimitive(it)) } })
    }

    // The relay names a subscription sha256(topic + clientId) when it does not return the id.
    private suspend fun rememberSubscription(topic: String, returnedId: String?) {
        subscriptionIds[topic] = returnedId ?: WcCrypto.hashMessage(topic + identity().clientId)
    }

    // Asks the relay for messages kept for the topics while nobody was subscribed. Best effort.
    private suspend fun fetchMessages(connection: Connection, topics: List<String>) {
        topics.chunked(BATCH_LIMIT).forEach { chunk -> fetchChunk(connection, chunk) }
    }

    private suspend fun fetchChunk(connection: Connection, topics: List<String>) {
        var round = 0
        var hasMore = true
        while (hasMore && round < MAX_FETCH_ROUNDS) {
            round += 1
            val result = runCatchingNonCancellation { relayRequest(connection, METHOD_BATCH_FETCH, topicsJson(topics)) }
                .getOrNull() as? JsonObject ?: return
            (result["messages"] as? JsonArray)?.forEach { item -> (item as? JsonObject)?.let(::deliver) }
            hasMore = (result["hasMore"] as? JsonPrimitive)?.booleanOrNull == true
        }
    }

    private suspend fun relayRequest(
        connection: Connection,
        method: String,
        params: JsonElement
    ): JsonElement {
        val id = ids.next()
        val response = CompletableDeferred<JsonElement>()
        pending[id] = response
        try {
            if (connection.socket?.send(WcJsonRpcCodec.encode(WcJsonRpc.Request(id, method, params))) != true) {
                throw WcRelayException("The relay connection is closed")
            }
            return withTimeout(config.requestTimeoutMillis) { response.await() }
        } catch (error: TimeoutCancellationException) {
            throw WcRelayException("The relay did not answer $method", error)
        } finally {
            pending.remove(id)
        }
    }

    private fun failPending() {
        val failed = pending.values.toList()
        pending.clear()
        failed.forEach { it.completeExceptionally(WcRelayException("The relay connection closed")) }
    }

    private fun onText(connection: Connection, text: String) {
        val root = WcJsonRpcCodec.parseObject(text.takeIf { it.length <= MAX_FRAME_LENGTH } ?: return) ?: return
        when ((root["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
            null -> onResponse(root)
            METHOD_SUBSCRIPTION -> onSubscription(connection, root)
            else -> Unit
        }
    }

    private fun onSubscription(connection: Connection, root: JsonObject) {
        val data = (root["params"] as? JsonObject)?.get("data") as? JsonObject
        val requestId = root["id"] as? JsonPrimitive
        // Acknowledged once queued; a message that did not fit is not acknowledged and comes again.
        if (data != null && requestId != null && deliver(data)) {
            connection.socket?.send(
                buildJsonObject {
                    put("id", requestId)
                    put("jsonrpc", "2.0")
                    put("result", true)
                }.toString()
            )
        }
    }

    // Queues one delivered message for onMessage; false when the inbox is full.
    private fun deliver(data: JsonObject): Boolean {
        val topic = (data["topic"] as? JsonPrimitive)?.content?.takeIf { it in watched }
        val message = (data["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.length <= MAX_MESSAGE_LENGTH }
        // Malformed or for a topic this wallet left: acknowledged and dropped.
        if (topic == null || message == null) return true
        val attestation = (data["attestation"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.length <= MAX_ATTESTATION_LENGTH }
        val publishedAt = (data["publishedAt"] as? JsonPrimitive)?.longOrNull
        return inbox.trySend(WcRelayMessage(topic, message, publishedAt, attestation)).isSuccess
    }

    private fun onResponse(root: JsonObject) {
        val id = (root["id"] as? JsonPrimitive)?.longOrNull ?: return
        val waiting = pending[id] ?: return
        val error = root["error"]
        val result = root["result"]
        when {
            error != null -> waiting.completeExceptionally(WcRelayException("The relay refused the call"))
            result != null -> waiting.complete(result)
            else -> waiting.completeExceptionally(WcRelayException("Malformed relay response"))
        }
    }

    private enum class ConnectionOutcome { STABLE, UNSTABLE, FAILED }

    private inner class Connection : WcRelaySocketListener {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Boolean>()

        @Volatile
        var socket: WcRelaySocket? = null

        override fun onOpen(socket: WcRelaySocket) {
            this.socket = socket
            opened.complete(Unit)
        }

        override fun onMessage(socket: WcRelaySocket, text: String) {
            this.socket = socket
            onText(this, text)
        }

        override fun onClosed(socket: WcRelaySocket, failure: Throwable?) {
            opened.completeExceptionally(WcRelayException("The relay closed the connection", failure))
            closed.complete(true)
            wake.trySend(Unit)
        }
    }

    companion object {
        const val METHOD_PUBLISH = "irn_publish"
        const val METHOD_SUBSCRIBE = "irn_subscribe"
        const val METHOD_BATCH_SUBSCRIBE = "irn_batchSubscribe"
        const val METHOD_UNSUBSCRIBE = "irn_unsubscribe"
        const val METHOD_BATCH_UNSUBSCRIBE = "irn_batchUnsubscribe"
        const val METHOD_BATCH_FETCH = "irn_batchFetchMessages"
        const val METHOD_SUBSCRIPTION = "irn_subscription"

        private const val BATCH_LIMIT = 500
        private const val MAX_FETCH_ROUNDS = 5
        private const val PUBLISH_ATTEMPTS = 2
        private const val INBOX_CAPACITY = 256
        private const val MAX_FRAME_LENGTH = 4 * 1024 * 1024
        private const val MAX_MESSAGE_LENGTH = 2 * 1024 * 1024
        private const val MAX_ATTESTATION_LENGTH = 8 * 1024
        private const val BACKGROUND_RECHECK_MILLIS = 5_000L
        private const val STABLE_CONNECTION_MILLIS = 10_000L
        private const val MIN_RECHECK_MILLIS = 10L
        private val BACKOFF_STEPS_MILLIS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000, 60_000)

        fun defaultBackoff(attempt: Int): Long =
            BACKOFF_STEPS_MILLIS[(attempt - 1).coerceIn(0, BACKOFF_STEPS_MILLIS.lastIndex)]
    }
}

/** Runs [block], rethrowing cancellation and returning any other failure. */
internal inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
