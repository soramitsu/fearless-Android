package jp.co.soramitsu.common.data.network.rpc.socket

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.base.RpcRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

interface RpcSocketListener {
    fun onResponse(rpcResponse: RpcResponse)

    fun onStateChanged(newState: WebSocketState)

    fun onConnected()
}

class RpcSocket(
    val url: String,
    listener: RpcSocketListener,
    val logger: Logger? = null,
    val factory: WebSocketFactory,
    val gson: Gson
) {
    val ws = factory.createSocket(url)

    init {
        setupListener(listener)
    }

    fun connectAsync() {
        logger?.log("[CONNECTING] $url")

        ws.connectAsynchronously()
    }

    fun clearListeners() {
        ws.clearListeners()
    }

    fun disconnect() {
        ws.disconnect()

        logger?.log("[DISCONNECTED] $url")
    }

    fun sendRpcRequest(rpcRequest: RpcRequest) {
        val text = gson.toJson(rpcRequest)

        logger?.log("[SENDING] $text")

        ws.sendText(text)
    }

    private fun setupListener(listener: RpcSocketListener) {
        ws.addListener(object : WebSocketAdapter() {
            override fun onTextMessage(websocket: WebSocket, text: String) {
                logger?.log("[RECEIVED] $text")

                listener.onResponse(gson.fromJson(text, RpcResponse::class.java))
            }

            override fun onError(websocket: WebSocket, cause: WebSocketException) {
                logger?.log("$[ERROR] ${cause.message}")
            }

            override fun onConnectError(websocket: WebSocket, exception: WebSocketException) {
                logger?.log("$[FAILED TO CONNECT] ${exception.message}")
            }

            override fun onStateChanged(websocket: WebSocket, newState: WebSocketState) {
                logger?.log("[STATE] $newState")

                listener.onStateChanged(newState)
            }

            override fun onConnected(
                websocket: WebSocket?,
                headers: MutableMap<String, MutableList<String>>?
            ) {
                logger?.log("[CONNECTED] $url")

                listener.onConnected()
            }
        })
    }
}