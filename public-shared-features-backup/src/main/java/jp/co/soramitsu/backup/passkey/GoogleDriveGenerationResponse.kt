package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded metadata only; no response body or token is included in parse errors. */
internal object GoogleDriveGenerationResponse {
    private const val MAX_BYTES = 8192
    private const val MAX_FIELDS = 8
    private const val MAX_TEXT_BYTES = 2048
    private val FILE_ID = Regex("^[A-Za-z0-9_-]{1,256}$")

    fun requireFileId(id: String) {
        require(FILE_ID.matches(id)) { "Invalid Drive generation file ID" }
    }

    fun allocatedId(body: ByteArray): String {
        val json = parse(body)
        require(json.keySet() == setOf("kind", "space", "ids")) { "Invalid Drive ID allocation" }
        require(json["kind"].requiredString() == "drive#generatedIds" && json["space"].requiredString() == "appDataFolder")
        val ids = json["ids"].asJsonArray
        require(ids.size() == 1) { "Invalid Drive ID allocation count" }
        return ids.single().requiredString().also(::requireFileId)
    }

    fun metadata(
        body: ByteArray,
        fileId: String,
        name: String,
        properties: JsonObject
    ): Int {
        val json = parse(body)
        require(json.keySet() == setOf("id", "name", "mimeType", "spaces", "appProperties", "size"))
        require(json["id"].requiredString() == fileId && json["name"].requiredString() == name)
        require(json["mimeType"].requiredString() == "application/octet-stream")
        require(json["spaces"].asJsonArray == JsonArray().apply { add("appDataFolder") })
        require(json["appProperties"] == properties) { "Drive generation properties mismatch" }
        val sizeText = json["size"].requiredString()
        val size = requireNotNull(sizeText.toIntOrNull()) { "Invalid Drive generation size" }
        require(size in 1..PasskeyBackupGenerationFormat.MAX_BYTES && size.toString() == sizeText)
        return size
    }

    private fun JsonElement.requiredString(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "Malformed Drive generation string" }
        return asString
    }

    private fun parse(body: ByteArray): JsonObject = runCatching {
        require(body.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body)).toString()
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = reader.value(0).asJsonObject
            require(reader.peek() == JsonToken.END_DOCUMENT)
            value
        }
    }.getOrElse { throw IllegalArgumentException("Malformed Drive generation metadata") }

    private fun JsonReader.value(depth: Int): JsonElement = when (peek()) {
        JsonToken.STRING -> JsonPrimitive(nextString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) })
        JsonToken.BEGIN_OBJECT -> {
            require(depth <= 1)
            val result = JsonObject()
            beginObject()
            while (hasNext()) {
                val name = nextName()
                require(name.length <= MAX_TEXT_BYTES && !result.has(name) && result.size() < MAX_FIELDS)
                result.add(name, value(depth + 1))
            }
            endObject()
            result
        }
        JsonToken.BEGIN_ARRAY -> {
            require(depth == 1)
            val result = JsonArray()
            beginArray()
            while (hasNext()) {
                require(result.size() < MAX_FIELDS && peek() == JsonToken.STRING)
                result.add(value(depth + 1))
            }
            endArray()
            result
        }
        else -> throw IllegalArgumentException("Malformed Drive generation field")
    }
}
