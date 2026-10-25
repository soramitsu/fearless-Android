package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URL

/** Plain JSON-RPC over HTTP for the opt-in live tests. Callers use read-only methods only. */
object LiveRpc {

    const val OPT_IN_VARIABLE = "FEARLESS_UTILS_LIVE_VALIDATE"

    private val gson = Gson()

    val enabled: Boolean
        get() = System.getenv(OPT_IN_VARIABLE) == "1"

    fun call(url: String, method: String, vararg params: Any): JsonElement = (URL(url).openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 60_000
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(gson.toJson(mapOf("id" to 1, "jsonrpc" to "2.0", "method" to method, "params" to params)).toByteArray()) }
        val response = gson.fromJson(inputStream.bufferedReader().use { it.readText() }, JsonObject::class.java)
        disconnect()
        response["result"] ?: error("$method failed: ${response["error"]}")
    }
}
