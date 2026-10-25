package jp.co.soramitsu.common.data.network.iroha

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/** Validates the wire envelope before Gson can default fields or coerce numeric IDs to strings. */
class IrohaMcpJsonRpcResponseAdapter : JsonDeserializer<IrohaMcpJsonRpcResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): IrohaMcpJsonRpcResponse {
        if (!json.isJsonObject) throw JsonParseException("MCP response is not an object")
        val envelope = json.asJsonObject
        val version = envelope.get("jsonrpc")
        val id = envelope.get("id")
        if (version?.isJsonPrimitive != true || !version.asJsonPrimitive.isString || version.asString != "2.0") {
            throw JsonParseException("MCP response must declare JSON-RPC 2.0")
        }
        if (id?.isJsonPrimitive != true || !id.asJsonPrimitive.isString) {
            throw JsonParseException("MCP response ID must be an explicit string")
        }
        val hasResult = envelope.has("result")
        val hasError = envelope.has("error")
        if (hasResult == hasError) throw JsonParseException("MCP response must contain exactly one result or error")
        val result = envelope.get("result")
        val error = envelope.get("error")
        // MCP method results are objects. Generic JSON-RPC null success is not a
        // valid MCP result and must not be confused with an absent payload.
        if (hasResult && !result.isJsonObject || hasError && !error.isJsonObject) {
            throw JsonParseException("MCP result or error must be an object")
        }
        return IrohaMcpJsonRpcResponse(
            jsonrpc = version.asString,
            id = id.asString,
            result = if (hasResult) context.deserialize<Any>(result, Any::class.java) else null,
            error = if (hasError) context.deserialize<IrohaMcpJsonRpcError>(error, IrohaMcpJsonRpcError::class.java) else null
        )
    }
}
