package jp.co.soramitsu.common.data.network.ton

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class TonIndexerJsonRpcRequest(
    @SerializedName("id")
    val id: Int = 1,
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: Map<String, Any?> = emptyMap()
)

data class TonIndexerJsonRpcResponse(
    @SerializedName("id")
    val id: JsonElement? = null,
    @SerializedName("jsonrpc")
    val jsonrpc: String? = null,
    @SerializedName("result")
    val result: JsonElement? = null,
    @SerializedName("error")
    val error: TonIndexerJsonRpcError? = null
)

data class TonIndexerJsonRpcError(
    @SerializedName("code")
    val code: Int? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: JsonElement? = null
)
