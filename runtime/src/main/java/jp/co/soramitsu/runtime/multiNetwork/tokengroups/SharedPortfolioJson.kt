package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Strict reader for the shared portfolio files: UTF-8 only, no lenient syntax, no duplicate
 * keys, no trailing content and a size cap. Field helpers reject unknown keys so a file in a
 * shape this build does not understand fails as a whole and its consumer falls back.
 */
internal object SharedPortfolioJson {
    const val MAX_FILE_BYTES = 256 * 1024
    private const val MAX_DEPTH = 8

    private val SHA256 = Regex("^[a-f0-9]{64}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun parseObject(bytes: ByteArray): JsonObject {
        require(bytes.size in 1..MAX_FILE_BYTES) { "Shared portfolio file must be 1..$MAX_FILE_BYTES bytes" }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

        return JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = reader.readValue(depth = 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing content after the shared portfolio document" }
            require(value.isJsonObject) { "Shared portfolio document must be a JSON object" }
            value.asJsonObject
        }
    }

    fun parseRegistryReference(json: JsonObject): SharedRegistryReference {
        json.requireKeys(
            where = "registry",
            allowed = setOf("source", "snapshot", "snapshotSha256", "appOwnedChains", "appOwnedChainsSha256", "reviewedAt", "reviewedBy"),
            required = setOf("source", "snapshotSha256", "appOwnedChainsSha256", "reviewedAt", "reviewedBy")
        )
        json.optionalString("snapshot")
        json.optionalString("appOwnedChains")
        json.requiredString("reviewedBy")
        val snapshotSha256 = json.requiredString("snapshotSha256")
        val appOwnedChainsSha256 = json.requiredString("appOwnedChainsSha256")
        val reviewedAt = json.requiredString("reviewedAt")
        require(snapshotSha256.matches(SHA256) && appOwnedChainsSha256.matches(SHA256)) { "registry digests must be lowercase sha256" }
        require(reviewedAt.matches(DATE)) { "registry.reviewedAt must be YYYY-MM-DD" }

        return SharedRegistryReference(
            source = json.requiredString("source"),
            snapshotSha256 = snapshotSha256,
            reviewedAt = reviewedAt
        )
    }

    fun JsonObject.requireKeys(where: String, allowed: Set<String>, required: Set<String>) {
        val unknown = keySet() - allowed
        require(unknown.isEmpty()) { "$where: unknown keys $unknown" }
        val missing = required - keySet()
        require(missing.isEmpty()) { "$where: missing keys $missing" }
    }

    fun JsonObject.requireFormat(format: String, version: Int) {
        require(requiredString("format") == format && requiredInt("version") == version) {
            "Unsupported shared portfolio format; expected $format v$version"
        }
    }

    fun JsonObject.requiredObject(name: String): JsonObject = requireNotNull(get(name)) { "$name is required" }.asObject(name)

    fun JsonObject.requiredArray(name: String): JsonArray {
        val value = requireNotNull(get(name)) { "$name is required" }
        require(value.isJsonArray) { "$name must be an array" }
        return value.asJsonArray
    }

    fun JsonObject.requiredString(name: String): String {
        val value = requireNotNull(get(name)) { "$name is required" }
        return value.asNonEmptyString(name)
    }

    /** Absent means null; when present the value must be a non-empty string. */
    fun JsonObject.optionalString(name: String): String? = get(name)?.asNonEmptyString(name)

    /** The key must be present; its value is a non-empty string or JSON null. */
    fun JsonObject.nullableString(name: String): String? {
        val value = requireNotNull(get(name)) { "$name is required" }
        return if (value.isJsonNull) null else value.asNonEmptyString(name)
    }

    fun JsonObject.requiredInt(name: String): Int {
        val value = requireNotNull(get(name)) { "$name is required" }
        return value.asExactInt(name)
    }

    /** The key must be present; its value is an integer or JSON null. */
    fun JsonObject.nullableInt(name: String): Int? {
        val value = requireNotNull(get(name)) { "$name is required" }
        return if (value.isJsonNull) null else value.asExactInt(name)
    }

    fun JsonElement.asObject(where: String): JsonObject {
        require(isJsonObject) { "$where must be an object" }
        return asJsonObject
    }

    private fun JsonElement.asNonEmptyString(name: String): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "$name must be a string" }
        return asString.also { require(it.isNotEmpty()) { "$name must not be empty" } }
    }

    private fun JsonElement.asExactInt(name: String): Int {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "$name must be a number" }
        return runCatching { asBigDecimal.intValueExact() }
            .getOrElse { throw IllegalArgumentException("$name must be an integer") }
    }

    private fun JsonReader.readValue(depth: Int): JsonElement {
        require(depth <= MAX_DEPTH) { "Shared portfolio document is nested too deeply" }
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val result = JsonObject()
                beginObject()
                while (hasNext()) {
                    val name = nextName()
                    require(!result.has(name)) { "Duplicate key $name" }
                    result.add(name, readValue(depth + 1))
                }
                endObject()
                result
            }
            JsonToken.BEGIN_ARRAY -> {
                val result = JsonArray()
                beginArray()
                while (hasNext()) {
                    result.add(readValue(depth + 1))
                }
                endArray()
                result
            }
            JsonToken.STRING -> JsonPrimitive(nextString())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(nextBoolean())
            JsonToken.NULL -> {
                nextNull()
                JsonNull.INSTANCE
            }
            else -> throw IllegalArgumentException("Unexpected JSON token ${peek()}")
        }
    }
}

/** The registry snapshot a shared file was reviewed against (`registry` in every shared file). */
data class SharedRegistryReference(
    val source: String,
    val snapshotSha256: String,
    val reviewedAt: String
)
