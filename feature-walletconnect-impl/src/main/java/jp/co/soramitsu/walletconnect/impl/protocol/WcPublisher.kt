package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/** Encrypts JSON-RPC messages for a topic and publishes them; tracks answers to the wallet's own requests. */
internal class WcPublisher(
    private val relay: WcRelayClient,
    private val store: WcStateStore,
    private val ids: WcIdGenerator
) {
    private val awaiting = ConcurrentHashMap<Long, CompletableDeferred<WcJsonRpc>>()

    fun nextId(): Long = ids.next()

    /** The key of [topic], from its session or pairing; null when the wallet has none. */
    fun symKeyOf(topic: String): ByteArray? {
        val state = store.state
        val hex = state.sessions.firstOrNull { it.topic == topic }?.symKey
            ?: state.pairings.firstOrNull { it.topic == topic }?.symKey
            ?: return null
        return WcEncoding.unhex(hex)
    }

    suspend fun send(
        topic: String,
        message: WcJsonRpc,
        options: WcRpcOptions,
        symKey: ByteArray? = null
    ) {
        val key = symKey ?: symKeyOf(topic) ?: error("No key for the topic")
        val encoded = WcCrypto.encodeType0(key, WcJsonRpcCodec.encode(message))
        relay.publish(topic, encoded, options.ttlSeconds, options.tag, options.prompt)
    }

    /** Publishes [message] as a type 1 envelope from a fresh key to [receiverPublicKey], on [topic]. */
    suspend fun sendType1(
        topic: String,
        receiverPublicKey: ByteArray,
        message: WcJsonRpc,
        options: WcRpcOptions
    ) {
        val encoded = WcCrypto.encodeType1(WcCrypto.generateKeyPair(), receiverPublicKey, WcJsonRpcCodec.encode(message))
        relay.publish(topic, encoded, options.ttlSeconds, options.tag, options.prompt)
    }

    /** Sends [request] and waits for the peer's answer. */
    suspend fun request(
        topic: String,
        request: WcJsonRpc.Request,
        options: WcRpcOptions,
        timeoutMillis: Long
    ): WcJsonRpc {
        val answer = CompletableDeferred<WcJsonRpc>()
        awaiting[request.id] = answer
        try {
            send(topic, request, options)
            return withTimeout(timeoutMillis) { answer.await() }
        } finally {
            awaiting.remove(request.id)
        }
    }

    /** Hands a response to the call waiting for it; false when nothing waits. */
    fun complete(response: WcJsonRpc): Boolean = awaiting.remove(response.id)?.complete(response) == true
}
