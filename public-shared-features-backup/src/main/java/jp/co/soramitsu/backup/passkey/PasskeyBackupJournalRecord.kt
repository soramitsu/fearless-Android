package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Platform-local canonical record. Only FPBKGEN1 bytes are portable cloud data. */
internal object PasskeyBackupJournalRecord {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_ATTEMPT_BYTES = 1024
    private const val MAX_FIELDS = 9
    private const val MAX_NAME_LENGTH = 32
    private const val RECORD_FORMAT = "FPBKJNL1"
    private const val ATTEMPT_FORMAT = "FPBKATT1"
    private const val COMMIT_FORMAT = "FPBKCOM1"
    private val recordKeys = setOf(
        "format", "schemaVersion", "operationId", "driveFileId", "bundleSize", "bundleSha256", "context", "bundleBase64url"
    )
    private val contextKeys = setOf(
        "ownerSubject", "backupNamespace", "generationId", "parentHeadRevision", "parentHeadSha256", "keyEpoch", "storageAccountBinding"
    )
    private val attemptKeys = setOf("format", "schemaVersion", "operationId", "driveFileId", "bundleSha256", "recordSha256")

    fun encode(operationId: String, candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate): ByteArray {
        PasskeyBackupGenerationFormat.requireIdentifier(operationId)
        val bytes = candidate.bytes
        PasskeyBackupGenerationFormat.decode(bytes, candidate.context, candidate.sha256)
        val context = candidate.context
        val json = JsonObject().apply {
            addProperty("format", RECORD_FORMAT)
            addProperty("schemaVersion", 1)
            addProperty("operationId", operationId)
            addProperty("driveFileId", candidate.fileId)
            addProperty("bundleSize", candidate.size.toString())
            addProperty("bundleSha256", candidate.sha256)
            addProperty("bundleBase64url", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
            add(
                "context",
                JsonObject().apply {
                addProperty("ownerSubject", context.ownerSubject)
                addProperty("backupNamespace", context.backupNamespace)
                addProperty("generationId", context.generationId)
                addProperty("parentHeadRevision", context.parentHeadRevision.toString())
                add("parentHeadSha256", context.parentHeadSha256?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                addProperty("keyEpoch", context.keyEpoch.toString())
                addProperty("storageAccountBinding", context.storageAccountBinding)
            }
            )
        }
        return canonical(json).also { require(it.size in 1..MAX_BYTES) { "Backup journal record too large" } }
    }

    fun decode(
        bytes: ByteArray,
        expectedOperationId: String,
        attemptRecorded: Boolean = false
    ): PasskeyBackupJournalEntry {
        PasskeyBackupGenerationFormat.requireIdentifier(expectedOperationId)
        val json = parse(bytes, MAX_BYTES)
        require(json.keySet() == recordKeys && json.string("format") == RECORD_FORMAT && json["schemaVersion"].toString() == "1")
        require(json.string("operationId") == expectedOperationId) { "Backup journal operation mismatch" }
        val contextJson = json["context"].asJsonObject
        require(contextJson.keySet() == contextKeys) { "Malformed backup journal context" }
        val parent = contextJson["parentHeadSha256"].takeUnless { it.isJsonNull }?.requiredString()
        val context = PasskeyBackupGeneration.Context(
            contextJson.string("ownerSubject"), contextJson.string("backupNamespace"), contextJson.string("generationId"),
            contextJson.decimal("parentHeadRevision"), parent, contextJson.decimal("keyEpoch"), contextJson.string("storageAccountBinding")
        )
        val encoded = json.string("bundleBase64url")
        val bundle = Base64.getUrlDecoder().decode(encoded)
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(bundle) == encoded) { "Invalid journal encoding" }
        require(json.decimal("bundleSize") == bundle.size.toLong()) { "Backup journal byte count mismatch" }
        PasskeyBackupGenerationFormat.decode(bundle, context, json.string("bundleSha256"))
        val candidate = GoogleDrivePasskeyBackupGenerationStorage.Candidate(json.string("driveFileId"), context, bundle)
        require(encode(expectedOperationId, candidate).contentEquals(bytes)) { "Noncanonical backup journal record" }
        return PasskeyBackupJournalEntry(expectedOperationId, candidate, PasskeyBackupGenerationFormat.sha256(bytes), attemptRecorded)
    }

    fun attempt(entry: PasskeyBackupJournalEntry): ByteArray = canonical(
        JsonObject().apply {
        addProperty("format", ATTEMPT_FORMAT)
        addProperty("schemaVersion", 1)
        addProperty("operationId", entry.operationId)
        addProperty("driveFileId", entry.candidate.fileId)
        addProperty("bundleSha256", entry.candidate.sha256)
        addProperty("recordSha256", entry.recordSha256)
    }
    )

    fun validateAttempt(bytes: ByteArray, entry: PasskeyBackupJournalEntry) {
        require(parse(bytes, MAX_ATTEMPT_BYTES).keySet() == attemptKeys && bytes.contentEquals(attempt(entry))) {
            "Malformed or conflicting backup journal attempt"
        }
    }

    /** Same exact-record binding as create admission, with a distinct stage domain. */
    fun commitAttempt(entry: PasskeyBackupJournalEntry): ByteArray = canonical(
        JsonObject().apply {
            addProperty("format", COMMIT_FORMAT)
            addProperty("schemaVersion", 1)
            addProperty("operationId", entry.operationId)
            addProperty("driveFileId", entry.candidate.fileId)
            addProperty("bundleSha256", entry.candidate.sha256)
            addProperty("recordSha256", entry.recordSha256)
        }
    )

    fun validateCommitAttempt(bytes: ByteArray, entry: PasskeyBackupJournalEntry) {
        require(parse(bytes, MAX_ATTEMPT_BYTES).keySet() == attemptKeys && bytes.contentEquals(commitAttempt(entry))) {
            "Malformed or conflicting backup commit attempt"
        }
    }

    private fun canonical(json: JsonObject): ByteArray = sorted(json).toString().toByteArray(Charsets.UTF_8)

    private fun sorted(value: JsonElement): JsonElement {
        if (!value.isJsonObject) return value
        val result = JsonObject()
        value.asJsonObject.keySet().sorted().forEach { result.add(it, sorted(value.asJsonObject[it])) }
        return result
    }

    private fun JsonElement.requiredString(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "Malformed backup journal string" }
        return asString
    }

    private fun JsonObject.string(key: String): String = get(key).requiredString()

    private fun JsonObject.decimal(key: String): Long {
        val text = string(key)
        val number = requireNotNull(text.toLongOrNull()) { "Invalid journal integer" }
        require(number >= 0 && number.toString() == text) { "Noncanonical journal integer" }
        return number
    }

    private fun parse(bytes: ByteArray, maximum: Int): JsonObject = runCatching {
        require(bytes.size in 1..maximum)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = reader.value(0).asJsonObject
            require(reader.peek() == JsonToken.END_DOCUMENT)
            value
        }
    }.getOrElse { throw IllegalArgumentException("Malformed backup journal JSON") }

    private fun JsonReader.value(depth: Int): JsonElement = when (peek()) {
        JsonToken.STRING -> JsonPrimitive(nextString())
        JsonToken.NUMBER -> JsonPrimitive(1).also { require(nextString() == "1") }
        JsonToken.NULL -> JsonNull.INSTANCE.also { nextNull() }
        JsonToken.BEGIN_OBJECT -> {
            require(depth <= 1)
            val result = JsonObject()
            beginObject()
            while (hasNext()) {
                val name = nextName()
                require(name.length <= MAX_NAME_LENGTH && !result.has(name) && result.size() < MAX_FIELDS)
                result.add(name, value(depth + 1))
            }
            endObject()
            result
        }
        else -> throw IllegalArgumentException("Malformed backup journal value")
    }
}
