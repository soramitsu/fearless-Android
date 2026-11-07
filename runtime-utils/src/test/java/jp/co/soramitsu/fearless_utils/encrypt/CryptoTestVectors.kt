package jp.co.soramitsu.fearless_utils.encrypt

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.security.MessageDigest

/** Test-vector loading and the synthetic inputs tools/upgrade-fixtures/android/LegacyCryptoFixtures.java uses. */
internal object CryptoTestVectors {

    val ED25519_ORDER: BigInteger = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)

    fun json(resource: String): JsonObject = JsonParser.parseString(text(resource)).asJsonObject

    fun text(resource: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "Missing test resource $resource" }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** SHA-256(label + "/" + counter) blocks, the generator's deterministic synthetic bytes. */
    fun stream(label: String, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        var counter = 0
        while (offset < length) {
            val block = MessageDigest.getInstance("SHA-256").digest("$label/$counter".encodeToByteArray())
            val take = minOf(block.size, length - offset)
            block.copyInto(out, offset, 0, take)
            offset += take
            counter++
        }
        return out
    }

    fun scalar(signature: ByteArray): BigInteger = BigInteger(1, signature.copyOfRange(32, 64).reversedArray())

    fun withScalar(signature: ByteArray, scalar: BigInteger): ByteArray {
        val bigEndian = scalar.toByteArray()
        val littleEndian = ByteArray(32) { index ->
            val source = bigEndian.size - 1 - index
            if (source >= 0) bigEndian[source] else 0
        }
        return signature.copyOfRange(0, 32) + littleEndian
    }

    fun flip(value: ByteArray, index: Int): ByteArray = value.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
}

internal fun JsonObject.string(name: String): String = get(name).asString

internal fun JsonObject.nullableString(name: String): String? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asString

internal fun JsonObject.array(name: String): JsonArray = getAsJsonArray(name)

internal fun JsonObject.objects(name: String): List<JsonObject> = array(name).map(JsonElement::getAsJsonObject)
