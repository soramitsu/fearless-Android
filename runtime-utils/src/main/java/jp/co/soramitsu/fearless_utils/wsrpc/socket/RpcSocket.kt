package jp.co.soramitsu.fearless_utils.wsrpc.socket

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import com.neovisionaries.ws.client.WebSocketState
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestSendGuard
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import java.util.Locale
import java.util.concurrent.TimeUnit

interface RpcSocketListener {
    fun onResponse(rpcResponse: RpcResponse)

    fun onResponse(subscriptionChange: SubscriptionChange)

    fun onStateChanged(newState: WebSocketState)

    fun onConnected()

    fun onError(error: Throwable)
}

private const val PING_INTERVAL_SECONDS = 30L

class RpcSocket(
    private val url: String,
    listener: RpcSocketListener,
    private val logger: Logger? = null,
    factory: WebSocketFactory,
    private val gson: Gson,
    private val transportPolicy: SocketTransportPolicy = SocketTransportPolicy.Platform
) {
    val ws = SocketTransportPolicy.createSocket(factory, url, transportPolicy)

    init {
        setupListener(listener)

        ws.pingInterval = TimeUnit.SECONDS.toMillis(PING_INTERVAL_SECONDS)
    }

    fun connectAsync() {
        SocketTransportPolicy.requireAllowed(url, transportPolicy)
        log("Connecting", "")

        ws.connectAsynchronously()
    }

    fun clearListeners() {
        ws.clearListeners()
    }

    fun disconnect() {
        ws.disconnect()

        log("Disconnected", "")
    }

    fun sendRpcRequest(rpcRequest: RpcRequest) {
        val text = gson.toJson(rpcRequest)

        log("Sending", "RPC message")

        ws.sendText(text)
    }

    /** Carries authorization through the WebSocket writer queue to the final output write/flush. */
    fun sendGuardedRpcRequest(rpcRequest: RpcRequest, guard: RequestSendGuard, onFailure: (Throwable) -> Unit): () -> Unit {
        val text = gson.toJson(rpcRequest)
        log("Sending guarded request", "prepared")
        val handle = ws.sendTextGuarded(text, { write -> guard.sendIfAuthorized { write.write() } }, onFailure)
        return { handle.cancel(); Unit }
    }

    private fun setupListener(listener: RpcSocketListener) {
        ws.addListener(object : WebSocketAdapter() {
            override fun onTextMessage(websocket: WebSocket, text: String) {
                log("Received", "RPC message")

                if (isSubscriptionChange(text)) {
                    listener.onResponse(gson.fromJson(text, SubscriptionChange::class.java))
                } else {
                    listener.onResponse(gson.fromJson(text, RpcResponse::class.java))
                }
            }

            override fun onPongFrame(websocket: WebSocket?, frame: WebSocketFrame?) {
                log("Received", "Pong")
            }

            override fun onError(websocket: WebSocket, cause: WebSocketException) {
                log("Error", cause.error)
                listener.onError(cause)
            }

            override fun onConnectError(websocket: WebSocket, exception: WebSocketException) {
                log("Failed to connect", exception.error)
                listener.onError(exception)
            }

            override fun onStateChanged(websocket: WebSocket, newState: WebSocketState) {
                log("State", newState)

                listener.onStateChanged(newState)
            }

            override fun onConnected(
                websocket: WebSocket?,
                headers: MutableMap<String, MutableList<String>>?
            ) {
                log("Connected", "")

                listener.onConnected()
            }
        })
    }

    private fun log(topic: String, message: Any?) {
        logger?.log("\t[SOCKET][${topic.uppercase(Locale.ROOT)}] $message")
    }

    private fun isSubscriptionChange(string: String): Boolean {
        return string.contains("\"subscription\":")
    }
}
