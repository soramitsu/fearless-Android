package co.jp.soramitsu.tonconnect.model

import org.json.JSONObject

data class SSEvent(
    val id: String?,
    val type: String?,
    val data: String
) {

    @Suppress("SwallowedException")
    val json: JSONObject by lazy {
        try {
            JSONObject(data)
        } catch (e: Throwable) {
            JSONObject()
        }
    }
}
