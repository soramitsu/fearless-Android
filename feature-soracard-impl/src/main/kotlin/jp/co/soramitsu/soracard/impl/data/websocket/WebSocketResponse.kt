package jp.co.soramitsu.soracard.impl.data.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebSocketResponse(
    @SerialName("json") val json: String
)
