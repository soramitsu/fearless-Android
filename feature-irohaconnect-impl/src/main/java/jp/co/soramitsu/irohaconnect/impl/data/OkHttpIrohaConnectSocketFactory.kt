package jp.co.soramitsu.irohaconnect.impl.data

import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSocket
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSocketFactory
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSocketListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

/**
 * Torii's Connect relay over OkHttp. The session token travels in the `Sec-WebSocket-Protocol` header, so every
 * interceptor (loggers included) is dropped. No WebSocket-level pings: the service keeps the session alive with
 * sequenced Connect Ping frames, and it only ever sends binary frames because Torii closes a socket on text.
 */
internal class OkHttpIrohaConnectSocketFactory(baseClient: OkHttpClient) : IrohaConnectSocketFactory {
    private val client = baseClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .pingInterval(0, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    override fun open(
        url: String,
        protocol: String,
        listener: IrohaConnectSocketListener
    ): IrohaConnectSocket {
        val request = Request.Builder()
            .url(url.replaceFirst("wss://", "https://"))
            .header(PROTOCOL_HEADER, protocol)
            .build()
        return OkHttpSocket(client.newWebSocket(request, Adapter(listener)))
    }

    private class OkHttpSocket(private val webSocket: WebSocket) : IrohaConnectSocket {
        override fun send(frame: ByteArray): Boolean = webSocket.send(frame.toByteString())

        override fun close(code: Int, reason: String) {
            if (!webSocket.close(code, reason)) webSocket.cancel()
        }
    }

    private class Adapter(private val listener: IrohaConnectSocketListener) : WebSocketListener() {
        private var closedReported = false

        override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = listener.onBinary(bytes.toByteArray())

        override fun onMessage(webSocket: WebSocket, text: String) = listener.onText()

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {
            webSocket.close(NORMAL_CLOSURE, null)
            reportClosed(code)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) = reportClosed(code)

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {
            synchronized(this) {
                if (closedReported) return
                closedReported = true
            }
            listener.onFailure(t)
        }

        private fun reportClosed(code: Int) {
            synchronized(this) {
                if (closedReported) return
                closedReported = true
            }
            listener.onClosed(code)
        }
    }

    private companion object {
        const val PROTOCOL_HEADER = "Sec-WebSocket-Protocol"
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val NORMAL_CLOSURE = 1000
    }
}
