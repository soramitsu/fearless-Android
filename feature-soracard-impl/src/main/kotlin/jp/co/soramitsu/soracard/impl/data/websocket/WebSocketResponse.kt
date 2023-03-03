package jp.co.soramitsu.soracard.impl.data.websocket

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WebSocketResponse(
    val json: String
)

fun Json.encodeToString(rpcRequest: WebSocketResponse): String {
    return encodeToString(rpcRequest)
}
