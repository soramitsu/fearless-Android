package jp.co.soramitsu.tonconnect.api.model

import org.json.JSONArray
import org.json.JSONObject

data class BridgeEvent(
    /** The bridge's SSE event id: transport bookkeeping, never a response id. */
    val eventId: Long,
    val message: Message,
    val connection: TonDappConnection,
) {

    val method: BridgeMethod
        get() = message.method

    /** The dApp's request id, which every response to this request must carry. */
    val requestId: Long
        get() = message.id

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
