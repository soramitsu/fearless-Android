package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import org.json.JSONArray
import org.json.JSONObject

data class FunctionInvokeBridgeMessage(
    val name: String,
    val args: JSONArray,
    val invocationId: String
): BridgeMessage(Type.InvokeRnFunc) {

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        args = json.getJSONArray("args"),
        invocationId = json.getString("invocationId")
    )

    override fun createJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("args", args)
        json.put("invocationId", invocationId)
        return json
    }

}