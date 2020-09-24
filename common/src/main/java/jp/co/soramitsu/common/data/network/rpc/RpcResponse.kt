package jp.co.soramitsu.common.data.network.rpc

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import org.spongycastle.util.encoders.Hex

class ScaleRpcResponse<S : Schema<S>>(val rpcVersion: String, val id: Int, val result: EncodableStruct<S>?) {
    companion object
}

fun <S : Schema<S>> ScaleRpcResponse.Companion.from(response: RpcResponse, schema: S): ScaleRpcResponse<S> {
    val hexResult = response.result as String?

    val result = if (hexResult != null) {
        val withoutPrefix = hexResult.removePrefix("0x")

        val resultByteArray = Hex.decode(withoutPrefix)
        val reader = ScaleCodecReader(resultByteArray)

        schema.read(reader)
    } else {
        null
    }

    return ScaleRpcResponse(response.jsonrpc, response.id, result)
}