package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A message the wallet published, as the relay received it. */
internal class Published(val topic: String, val message: String, val ttl: Long, val tag: Int, val prompt: Boolean)

/**
 * An in-memory WalletConnect relay: answers the IRN calls, keeps messages for topics nobody
 * subscribed yet (the relay's mailbox) and delivers them as `irn_subscription` once someone does.
 */
internal class WcTestRelay : WcRelayTransport {
    val urls = CopyOnWriteArrayList<String>()
    val calls = CopyOnWriteArrayList<String>()
    val subscriptions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val published = CopyOnWriteArrayList<Published>()
    val acknowledged = CopyOnWriteArrayList<JsonElement>()

    /** Methods this relay receives but never answers. */
    val unanswered: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mailbox = ConcurrentHashMap<String, MutableList<Pair<String, String?>>>()

    @Volatile
    var failNextConnects = 0

    @Volatile
    var current: Socket? = null

    @Volatile
    var onPublish: (Published) -> Unit = {}

    private var deliveryId = 18_000_000_000_000_000_000uL

    override fun open(url: String, listener: WcRelaySocketListener): WcRelaySocket {
        urls += url
        val socket = Socket(listener)
        if (failNextConnects > 0) {
            failNextConnects -= 1
            socket.open = false
            listener.onClosed(socket, IOException("connection refused"))
            return socket
        }
        current = socket
        listener.onOpen(socket)
        return socket
    }

    /** What a dApp publishes for [topic]: delivered now when the wallet is subscribed, otherwise kept. */
    fun push(
        topic: String,
        message: String,
        attestation: String? = null
    ) {
        val socket = current
        if (socket != null && socket.open && topic in subscriptions) {
            deliver(socket, topic, message, attestation)
        } else {
            mailbox.getOrPut(topic) { CopyOnWriteArrayList() } += message to attestation
        }
    }

    /** Drops the connection as a network failure would. */
    fun dropConnection() {
        val socket = current ?: return
        socket.open = false
        current = null
        socket.listener.onClosed(socket, IOException("network lost"))
    }

    fun publishedOn(topic: String) = published.filter { it.topic == topic }

    @Synchronized
    private fun nextDeliveryId(): String {
        deliveryId += 1u
        return deliveryId.toString()
    }

    private fun deliver(
        socket: Socket,
        topic: String,
        message: String,
        attestation: String?
    ) {
        val data = buildJsonObject {
            put("topic", topic)
            put("message", message)
            put("publishedAt", 1_700_000_000_000)
            attestation?.let { put("attestation", it) }
        }
        // Ids beyond Long.MAX_VALUE, as a relay may use: the wallet must echo them exactly.
        val text = """{"id":${nextDeliveryId()},"jsonrpc":"2.0","method":"irn_subscription","params":{"id":"sub","data":$data}}"""
        socket.listener.onMessage(socket, text)
    }

    private fun handle(socket: Socket, text: String) {
        val root = Json.parseToJsonElement(text).jsonObject
        val method = (root["method"] as? JsonPrimitive)?.content
        if (method == null) {
            acknowledged += root["id"]!!
            return
        }
        calls += method
        if (method in unanswered) return
        val id = root["id"]!!.jsonPrimitive.long
        val params = root["params"]!!.jsonObject
        when (method) {
            WcRelayClient.METHOD_SUBSCRIBE -> {
                val topic = params["topic"]!!.jsonPrimitive.content
                subscriptions += topic
                reply(socket, id, JsonPrimitive("subscription-$topic"))
                flushMailbox(socket, topic)
            }
            WcRelayClient.METHOD_BATCH_SUBSCRIBE -> {
                val topics = params["topics"]!!.jsonArray.map { it.jsonPrimitive.content }
                subscriptions += topics
                reply(socket, id, JsonArray(topics.map { JsonPrimitive("subscription-$it") }))
                topics.forEach { flushMailbox(socket, it) }
            }
            WcRelayClient.METHOD_UNSUBSCRIBE -> {
                subscriptions -= params["topic"]!!.jsonPrimitive.content
                reply(socket, id, JsonPrimitive(true))
            }
            WcRelayClient.METHOD_BATCH_UNSUBSCRIBE -> {
                params["subscriptions"]!!.jsonArray.forEach { subscriptions -= it.jsonObject["topic"]!!.jsonPrimitive.content }
                reply(socket, id, JsonPrimitive(true))
            }
            WcRelayClient.METHOD_BATCH_FETCH -> reply(
                socket,
                id,
                buildJsonObject {
                    put("messages", JsonArray(emptyList()))
                    put("hasMore", false)
                }
            )
            WcRelayClient.METHOD_PUBLISH -> {
                val publication = Published(
                    topic = params["topic"]!!.jsonPrimitive.content,
                    message = params["message"]!!.jsonPrimitive.content,
                    ttl = params["ttl"]!!.jsonPrimitive.long,
                    tag = params["tag"]!!.jsonPrimitive.content.toInt(),
                    prompt = params["prompt"]?.jsonPrimitive?.content == "true"
                )
                published += publication
                reply(socket, id, JsonPrimitive(true))
                onPublish(publication)
            }
            else -> error("unexpected relay call $method")
        }
    }

    private fun flushMailbox(socket: Socket, topic: String) {
        mailbox.remove(topic)?.forEach { (message, attestation) -> deliver(socket, topic, message, attestation) }
    }

    private fun reply(
        socket: Socket,
        id: Long,
        result: JsonElement
    ) {
        socket.listener.onMessage(
            socket,
            buildJsonObject {
                put("id", id)
                put("jsonrpc", "2.0")
                put("result", result)
            }.toString()
        )
    }

    inner class Socket(val listener: WcRelaySocketListener) : WcRelaySocket {
        @Volatile
        var open = true

        override fun send(text: String): Boolean {
            if (!open) return false
            handle(this, text)
            return true
        }

        override fun close() {
            if (!open) return
            open = false
            if (current === this) current = null
            listener.onClosed(this, null)
        }
    }
}

/** Waits up to five seconds for [condition]. */
internal fun eventually(message: String = "condition not met", condition: () -> Boolean) = runBlocking {
    withTimeout(5_000) {
        while (!condition()) delay(10)
    }
    check(condition()) { message }
}

internal fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
