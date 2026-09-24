package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Read-only, disabled candidate for an authenticated owner backup head. The selected-account
 * token provider verifies the Google subject used to derive the expected storage binding.
 * A head is metadata; it neither decrypts a wallet nor authorizes its installation.
 */
class PasskeyBackupOwnerHeadHttpClient(
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(OkHttpClient()),
    baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    suspend fun readHead(session: PasskeyBackupOwnerSession): PasskeyBackupAuthenticatedHead {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        requireSession(session)
        currentCoroutineContext().ensureActive()
        val selectedSubject = tokenProvider.accessToken().subject
        currentCoroutineContext().ensureActive()
        val expectedStorageAccountBinding = PasskeyBackupGenerationFormat.storageAccountBinding(selectedSubject)
        val response = transport.execute(
            GoogleDriveHttpRequest(
                method = "POST",
                url = "$normalizedBaseUrl$HEAD_PATH",
                headers = mapOf(
                    "Authorization" to "Bearer ${session.sessionToken}",
                    "Content-Type" to "application/json; charset=utf-8",
                    "Cache-Control" to "no-store"
                ),
                body = HEAD_BODY.copyOf(),
                maxResponseBytes = MAX_HEAD_RESPONSE_BYTES
            )
        )
        currentCoroutineContext().ensureActive()
        require(response.code == HTTP_OK) { "Owner backup head request failed with HTTP ${response.code}" }
        requireSession(session)
        require(tokenProvider.accessToken().subject == selectedSubject) { "Google Drive selected account changed" }
        currentCoroutineContext().ensureActive()
        return OwnerHeadResponseParser.decode(response.body, session, expectedStorageAccountBinding)
    }

    private fun requireSession(session: PasskeyBackupOwnerSession) {
        PasskeyBackupGenerationFormat.requireIdentifier(session.sessionToken, "session.")
        PasskeyBackupGenerationFormat.requireIdentifier(session.subject, "owner:")
        PasskeyBackupGenerationFormat.requireIdentifier(session.namespace, "backup:")
        require(session.platform == ANDROID_PLATFORM && session.generation >= 0) { "Invalid owner session" }
        val currentTimeMillis = nowMillis()
        require(currentTimeMillis >= 0) { "Invalid owner session clock" }
        val nowSeconds = currentTimeMillis / MILLIS_PER_SECOND
        require(session.expiresAt > nowSeconds && session.expiresAt <= nowSeconds + MAX_SESSION_AHEAD_SECONDS) {
            "Owner session is expired or has an invalid lifetime"
        }
    }

    private companion object {
        const val HEAD_PATH = "/api/passkey-backup/v1/owner/backup/head"
        const val HTTP_OK = 200
        const val MAX_HEAD_RESPONSE_BYTES = 8 * 1024
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_SESSION_AHEAD_SECONDS = 660L
        const val ANDROID_PLATFORM = "android"
        val HEAD_BODY = "{\"schemaVersion\":1}".toByteArray(Charsets.UTF_8)
    }
}

/** Reject duplicate decoded keys, coercion, excessive depth and trailing data before Gson parsing. */
private object OwnerHeadResponseParser {
    private const val MAX_RESPONSE_BYTES = 8 * 1024
    private const val MAX_OBJECT_FIELDS = 12
    private const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L
    private val DECIMAL = Regex("^(0|[1-9][0-9]*)$")
    private val ROOT_FIELDS = setOf("schemaVersion", "ownerSubject", "backupNamespace", "head", "previous")
    private val DESCRIPTOR_FIELDS = setOf(
        "headRevision", "parentHeadRevision", "parentHeadSha256", "generationId",
        "bundleSha256", "keyEpoch", "driveFileId", "storageAccountBinding"
    )

    fun decode(
        body: ByteArray,
        session: PasskeyBackupOwnerSession,
        expectedStorageAccountBinding: String
    ): PasskeyBackupAuthenticatedHead = try {
        require(body.size in 1..MAX_RESPONSE_BYTES)
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body)).toString()
        validateJson(text)
        val root = JsonParser.parseString(text).asJsonObject
        require(root.keySet() == ROOT_FIELDS && root.number("schemaVersion") == "1")
        PasskeyBackupAuthenticatedHead(
            ownerSubject = root.string("ownerSubject"),
            backupNamespace = root.string("backupNamespace"),
            head = descriptor(root.get("head")),
            previous = descriptor(root.get("previous")),
            expectedOwnerSubject = session.subject,
            expectedBackupNamespace = session.namespace,
            expectedStorageAccountBinding = expectedStorageAccountBinding
        )
    } catch (_: Exception) {
        // Parser exceptions can include untrusted response fragments in their diagnostic text.
        throw IllegalArgumentException("Owner backup head response is malformed")
    }

    private fun validateJson(text: String) {
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            readObject(reader, depth = 0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
    }

    private fun readObject(reader: JsonReader, depth: Int) {
        require(depth <= 1 && reader.peek() == JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        val seen = mutableSetOf<String>()
        while (reader.hasNext()) {
            require(seen.add(reader.nextName()) && seen.size <= MAX_OBJECT_FIELDS)
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.NULL -> reader.nextNull()
                JsonToken.BEGIN_OBJECT -> {
                    require(depth == 0)
                    readObject(reader, depth + 1)
                }
                else -> error("Unexpected owner backup head JSON type")
            }
        }
        reader.endObject()
    }

    private fun descriptor(value: com.google.gson.JsonElement?): PasskeyBackupHeadDescriptor? {
        if (value?.isJsonNull == true) return null
        require(value != null && value.isJsonObject)
        val objectValue = value.asJsonObject
        require(objectValue.keySet() == DESCRIPTOR_FIELDS)
        val parentValue = objectValue.get("parentHeadSha256")
        val parentDigest = if (parentValue?.isJsonNull == true) null else objectValue.string("parentHeadSha256")
        return PasskeyBackupHeadDescriptor(
            headRevision = decimal(objectValue.string("headRevision")),
            parentHeadRevision = decimal(objectValue.string("parentHeadRevision")),
            parentHeadSha256 = parentDigest,
            generationId = objectValue.string("generationId"),
            bundleSha256 = objectValue.string("bundleSha256"),
            keyEpoch = decimal(objectValue.string("keyEpoch")),
            driveFileId = objectValue.string("driveFileId"),
            storageAccountBinding = objectValue.string("storageAccountBinding")
        )
    }

    private fun decimal(value: String): Long {
        require(DECIMAL.matches(value))
        return value.toLongOrNull()?.takeIf { it <= MAX_SAFE_JS_INTEGER }
            ?: throw IllegalArgumentException("Invalid owner backup head integer")
    }

    private fun JsonObject.string(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.number(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asString
    }
}
