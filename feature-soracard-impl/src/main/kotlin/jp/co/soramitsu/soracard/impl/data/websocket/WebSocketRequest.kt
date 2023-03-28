package jp.co.soramitsu.soracard.impl.data.websocket

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketRequest(
    val json: String
)
