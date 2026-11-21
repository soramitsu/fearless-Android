package jp.co.soramitsu.walletconnect.impl.protocol

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** One WebSocket to the relay. */
internal interface WcRelaySocket {
    /** False when the socket is closed or its send queue is full. */
    fun send(text: String): Boolean

    fun close()
}

internal interface WcRelaySocketListener {
    fun onOpen(socket: WcRelaySocket)

    fun onMessage(socket: WcRelaySocket, text: String)

    /** Called once, when the socket closed or failed. */
    fun onClosed(socket: WcRelaySocket, failure: Throwable?)
}

/** Opens relay sockets; the engine's tests replace it with an in-memory relay. */
internal fun interface WcRelayTransport {
    fun open(url: String, listener: WcRelaySocketListener): WcRelaySocket
}

/**
 * The relay over OkHttp WebSockets. Pings detect a dead connection, which the client then reopens.
 * Interceptors are dropped: the connect URL carries the project id and the auth JWT, and no logger
 * may ever see it.
 */
internal class OkHttpRelayTransport(baseClient: OkHttpClient) : WcRelayTransport {
    private val client = baseClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    override fun open(url: String, listener: WcRelaySocketListener): WcRelaySocket {
        val socket = OkHttpRelaySocket()
        socket.webSocket = client.newWebSocket(Request.Builder().url(url).build(), socket.adapter(listener))
        return socket
    }

    private class OkHttpRelaySocket : WcRelaySocket {
        @Volatile
        var webSocket: WebSocket? = null

        override fun send(text: String): Boolean = webSocket?.send(text) == true

        override fun close() {
            webSocket?.close(NORMAL_CLOSURE, null)
        }

        fun adapter(listener: WcRelaySocketListener) = object : WebSocketListener() {
            private var closedReported = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@OkHttpRelaySocket.webSocket = webSocket
                listener.onOpen(this@OkHttpRelaySocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(this@OkHttpRelaySocket, text)
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                webSocket.close(NORMAL_CLOSURE, null)
                reportClosed(null)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                reportClosed(null)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                reportClosed(t)
            }

            @Synchronized
            private fun reportClosed(failure: Throwable?) {
                if (closedReported) return
                closedReported = true
                listener.onClosed(this@OkHttpRelaySocket, failure)
            }
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val PING_INTERVAL_SECONDS = 30L
        const val CONNECT_TIMEOUT_SECONDS = 20L
    }
}
