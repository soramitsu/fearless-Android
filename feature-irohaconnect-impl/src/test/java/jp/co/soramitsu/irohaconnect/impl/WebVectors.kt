package jp.co.soramitsu.irohaconnect.impl

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding

/**
 * Vectors the browser wallet's implementation (fearless-wallet-web PR #1122 @6f1fc6b5) and the upstream iroha-js
 * 0.0.3 app SDK produced; regenerate them with `src/test/vector-generator/generate.sh`.
 */
object WebVectors {
    val root: JsonObject by lazy { load("irohaconnect/web-vectors.json") }
    val openCapture: JsonObject by lazy { load("irohaconnect/taira-open-frame-20261121.json") }
    val accountAddress: String get() = root.obj("account").str("address")
    val accountPublicKeyHex: String get() = root.obj("account").str("publicKeyHex")
    val mnemonic: String get() = root.str("mnemonic")

    fun load(resource: String): JsonObject {
        val stream = requireNotNull(WebVectors::class.java.classLoader!!.getResourceAsStream(resource)) { "missing $resource" }
        return stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    }

    fun JsonObject.str(name: String): String = get(name).asString
    fun JsonObject.strOrNull(name: String): String? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asString
    fun JsonObject.bytes(name: String): ByteArray = IrohaConnectEncoding.unhex(str(name))
    fun JsonObject.obj(name: String): JsonObject = getAsJsonObject(name)
    fun JsonObject.array(name: String): JsonArray = getAsJsonArray(name)
    fun JsonArray.objects(): List<JsonObject> = map { it.asJsonObject }
}
