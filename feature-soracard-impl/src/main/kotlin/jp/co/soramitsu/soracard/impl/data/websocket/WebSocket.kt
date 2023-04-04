package jp.co.soramitsu.soracard.impl.data.websocket

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import jp.co.soramitsu.xnetworking.networkclient.NetworkClientConfig
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuHttpClientProvider
import jp.co.soramitsu.xnetworking.networkclient.WebSocketClientConfig
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface WebSocketListener {

    suspend fun onResponse(response: WebSocketResponse)

    fun onSocketClosed()

    fun onConnected()
}

class WebSocket(
    private val url: String,
    private var listener: WebSocketListener,
    private val json: Json,
    connectTimeoutMillis: Long = 10_000,
    pingInterval: Long = 20,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    logging: Boolean = false,
    provider: SoramitsuHttpClientProvider
) {

    private var socketSession: DefaultClientWebSocketSession? = null

    private val networkClient = provider.provide(
        NetworkClientConfig(
            logging = logging,
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS,
            json = json,
            webSocketClientConfig = WebSocketClientConfig(
                pingInterval = pingInterval,
                maxFrameSize = maxFrameSize
            )
        )
    )

    private suspend fun DefaultClientWebSocketSession.listenIncomingMessages() {
        try {
            incoming.receiveAsFlow()
                .filter { it is Frame.Text }
                .collect { frame ->
                    frame as Frame.Text
                    val text = frame.readText()

                    this@WebSocket.listener.onResponse(response = WebSocketResponse(json = text))
                }
        } catch (e: Exception) {
            println("Error while receiving: ${e.message}")
        }
    }

    suspend fun disconnect() {
        socketSession?.close()
    }

    suspend fun sendRequest(request: WebSocketRequest) {
        networkClient.webSocket(url) {
            try {
                socketSession = this
                listener.onConnected()

                launch {
                    socketSession?.send(Frame.Text(request.json))
                }

                listenIncomingMessages()
            } catch (e: Exception) {
                println("Error while connecting")
            } finally {
                listener.onSocketClosed()
            }
        }
    }
}
