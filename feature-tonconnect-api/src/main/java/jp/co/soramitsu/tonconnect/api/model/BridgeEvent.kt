package jp.co.soramitsu.tonconnect.api.model

import org.json.JSONArray
import org.json.JSONObject

data class BridgeEvent(
    val eventId: Long,
    val message: Message,
    val connection: TonDappConnection,
) {

    val method: BridgeMethod
        get() = message.method

    data class Message(
        val method: BridgeMethod,
        val params: List<String>,
        val id: Long,
    ) {

        constructor(json: JSONObject) : this(
            BridgeMethod.of(json.getString("method")),
            json.getJSONArray("params").toStringList(),
            json.getLongCompat("id"),
        )

        companion object {

            fun parse(array: JSONArray): List<Message> {
                val messages = mutableListOf<Message>()
                for (i in 0 until array.length()) {
                    messages.add(Message(array.getJSONObject(i)))
                }
                return messages
            }
        }
    }
}
