package jp.co.soramitsu.fearless_utils.wsrpc.request.base

import com.google.gson.annotations.SerializedName

abstract class RpcRequest(
    @SerializedName("jsonrpc")
    val jsonRpc: String = "2.0"
)
