package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/** A JSON-RPC 2.0 message, as WalletConnect peers and the relay exchange them. */
internal sealed interface WcJsonRpc {
    val id: Long

    data class Request(override val id: Long, val method: String, val params: JsonElement) : WcJsonRpc

    data class Result(override val id: Long, val result: JsonElement) : WcJsonRpc

    data class Error(override val id: Long, val code: Long, val message: String) : WcJsonRpc
}

internal object WcJsonRpcCodec {
    private const val VERSION = "2.0"
    private const val MAX_ERROR_MESSAGE_LENGTH = 1024

    val json = Json { ignoreUnknownKeys = true }

    fun encode(message: WcJsonRpc): String = buildJsonObject {
        put("id", message.id)
        put("jsonrpc", VERSION)
        when (message) {
            is WcJsonRpc.Request -> {
                put("method", message.method)
                put("params", message.params)
            }
            is WcJsonRpc.Result -> put("result", message.result)
            is WcJsonRpc.Error -> put(
                "error",
                buildJsonObject {
                    put("code", message.code)
                    put("message", message.message)
                }
            )
        }
    }.toString()

    /** The message in [text], or null when it is not a JSON-RPC 2.0 request or response. */
    fun decode(text: String): WcJsonRpc? {
        val root = parseObject(text)?.takeIf { it.hasVersion() } ?: return null
        val id = (root["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null
        val method = root["method"]
        val result = root["result"]
        val error = root["error"]
        return when {
            method != null -> (method as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { name -> WcJsonRpc.Request(id, name, root["params"] ?: JsonNull) }
            result != null -> WcJsonRpc.Result(id, result)
            error is JsonObject -> (error["code"] as? JsonPrimitive)?.longOrNull?.let { code ->
                WcJsonRpc.Error(id, code, (error["message"] as? JsonPrimitive)?.content.orEmpty().take(MAX_ERROR_MESSAGE_LENGTH))
            }
            else -> null
        }
    }

    // A message without a version is read as 2.0; one naming any other version is not JSON-RPC 2.0.
    private fun JsonObject.hasVersion(): Boolean = this["jsonrpc"].let { it == null || (it as? JsonPrimitive)?.content == VERSION }

    fun parseObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (ignored: SerializationException) {
        null
    } catch (ignored: IllegalArgumentException) {
        null
    }
}

/**
 * JSON-RPC ids as the JavaScript SDK makes them: milliseconds × 1000 plus a counter, so they stay
 * below 2^53 and every peer reads them as exact numbers. Strictly increasing within the process.
 */
internal class WcIdGenerator(private val clock: WcClock) {
    private val last = AtomicLong()

    fun next(): Long {
        val candidate = clock.nowMillis() * ID_MULTIPLIER
        return last.updateAndGet { previous -> if (candidate > previous) candidate else previous + 1 }
    }

    private companion object {
        const val ID_MULTIPLIER = 1000L
    }
}

/** Wall-clock time, replaceable in tests. */
internal interface WcClock {
    fun nowMillis(): Long

    fun nowSeconds(): Long = nowMillis() / MILLIS_PER_SECOND

    companion object {
        const val MILLIS_PER_SECOND = 1000L

        val System: WcClock = object : WcClock {
            override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
        }
    }
}
