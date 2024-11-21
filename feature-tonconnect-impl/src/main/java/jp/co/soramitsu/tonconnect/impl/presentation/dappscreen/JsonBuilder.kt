package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import org.json.JSONArray
import org.json.JSONObject

internal object JsonBuilder {

    fun responseError(id: Long, error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("error", error(error))
        json.put("id", id)
        return json
    }

    fun error(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("code", error.code)
        json.put("message", error.message)
        return json
    }

    fun connectEventError(error: BridgeError): JSONObject {
        val json = JSONObject()
        json.put("event", "connect_error")
        json.put("id", System.currentTimeMillis())
        json.put("payload", error(error))
        return json
    }


    fun device(maxMessages: Int, appVersion: String): JSONObject {
        val json = JSONObject()
        json.put("platform", "android")
        json.put("appName", "Tonkeeper")
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", 2)
        json.put("features", features(maxMessages))
        return json
    }

    private fun features(maxMessages: Int): JSONArray {
        val array = JSONArray()
        array.put("SendTransaction")
        array.put(sendTransactionFeature(maxMessages))
        return array
    }

    private fun sendTransactionFeature(maxMessages: Int): JSONObject {
        val json = JSONObject()
        json.put("name", "SendTransaction")
        json.put("maxMessages", maxMessages)
        return json
    }
}