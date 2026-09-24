package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Public metadata prepared against an authenticated owner head, not proof of a decryptable backup. */
class PasskeyBackupGenerationMetadata(
    val operationId: String,
    val context: PasskeyBackupGeneration.Context,
    val bundleSha256: String,
    val driveFileId: String,
    authenticatedHead: PasskeyBackupAuthenticatedHead,
) {
    val expectedHeadRevision: Long = authenticatedHead.head?.headRevision ?: 0L
    val expectedHeadSha256: String? = authenticatedHead.head?.bundleSha256
    private val requestBody: ByteArray
    private val requestDigest: String

    init {
        PasskeyBackupGenerationFormat.requireIdentifier(operationId)
        require(operationId != context.generationId) { "Backup operation and generation IDs must differ" }
        PasskeyBackupGenerationFormat.requireSha256(bundleSha256)
        GoogleDriveGenerationResponse.requireFileId(driveFileId)
        require(
            context.ownerSubject == authenticatedHead.ownerSubject &&
                context.backupNamespace == authenticatedHead.backupNamespace &&
                context.parentHeadRevision == expectedHeadRevision &&
                context.parentHeadSha256 == expectedHeadSha256,
        ) { "Backup generation does not extend the authenticated head" }
        authenticatedHead.head?.let {
            require(it.storageAccountBinding == context.storageAccountBinding) {
                "Authenticated backup head uses another storage account"
            }
        }
        authenticatedHead.previous?.let {
            require(it.storageAccountBinding == context.storageAccountBinding) {
                "Previous backup head uses another storage account"
            }
        }
        require(expectedHeadRevision < MAX_SAFE_JS_INTEGER && context.keyEpoch <= MAX_SAFE_JS_INTEGER) {
            "Backup generation revision or epoch is out of range"
        }
        requestBody =
            JsonObject().apply {
                addProperty("schemaVersion", 1)
                addProperty("operationId", operationId)
                addProperty("generationId", context.generationId)
                addProperty("backupNamespace", context.backupNamespace)
                addProperty("expectedHeadRevision", expectedHeadRevision.toString())
                add("expectedHeadSha256", expectedHeadSha256?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                addProperty("bundleSha256", bundleSha256)
                addProperty("keyEpoch", context.keyEpoch.toString())
                addProperty("driveFileId", driveFileId)
                addProperty("storageAccountBinding", context.storageAccountBinding)
            }.toString().toByteArray(Charsets.UTF_8)
        requestDigest = PasskeyBackupGenerationFormat.sha256(requestBody)
    }

    internal fun body(): ByteArray = requestBody.copyOf()

    internal fun requireScope(session: PasskeyBackupOwnerSession, storageBinding: String) {
        require(context.ownerSubject == session.subject && context.backupNamespace == session.namespace) {
            "Backup generation owner mismatch"
        }
        require(context.storageAccountBinding == storageBinding) { "Backup generation storage account mismatch" }
    }

    internal fun requireDescriptor(descriptor: PasskeyBackupHeadDescriptor) {
        require(
            descriptor.headRevision == expectedHeadRevision + 1 &&
                descriptor.parentHeadRevision == expectedHeadRevision &&
                descriptor.parentHeadSha256 == expectedHeadSha256 &&
                descriptor.generationId == context.generationId &&
                descriptor.bundleSha256 == bundleSha256 &&
                descriptor.keyEpoch == context.keyEpoch &&
                descriptor.driveFileId == driveFileId &&
                descriptor.storageAccountBinding == context.storageAccountBinding,
        ) { "Backup generation operation does not match the prepared candidate" }
    }

    internal fun digest(): String = requestDigest

    override fun toString(): String = "PasskeyBackupGenerationMetadata(redacted)"

    private companion object {
        const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L
    }
}

/** A one-use, exact-metadata authority grant; its token is never included in diagnostics. */
class PasskeyBackupGenerationGrant internal constructor(
    @Transient val token: String,
    val expiresAt: Long,
    private val sessionToken: String,
    private val requestDigest: String,
) {
    internal fun requireFor(
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
        nowSeconds: Long,
    ) {
        require(session.sessionToken == sessionToken && metadata.digest() == requestDigest && expiresAt > nowSeconds) {
            "Backup generation grant does not match the live request"
        }
    }

    override fun toString(): String = "PasskeyBackupGenerationGrant(redacted)"
}

sealed interface PasskeyBackupGenerationOperationStatus {
    data object Absent : PasskeyBackupGenerationOperationStatus

    class Committed(val descriptor: PasskeyBackupHeadDescriptor) : PasskeyBackupGenerationOperationStatus {
        override fun toString(): String = "PasskeyBackupGenerationOperationStatus.Committed(redacted)"
    }
}

/**
 * Disabled HTTP candidate for the owner's metadata-only generation grant, commit and status routes.
 * The caller must independently upload, download, decrypt and prove every original key before commit.
 */
class PasskeyBackupOwnerGenerationHttpClient(
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(OkHttpClient()),
    baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED,
) {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    suspend fun grant(
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
    ): PasskeyBackupGenerationGrant {
        val body = metadata.body()
        val response = execute(session, metadata, GRANT_PATH, "session.", session.sessionToken, body)
        val grant = OwnerGenerationResponseParser.grant(response.body, session, metadata, nowSeconds())
        currentCoroutineContext().ensureActive()
        return grant
    }

    /** A successful metadata CAS is not backup-completion evidence; the caller owns local verification. */
    suspend fun commit(
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
        grant: PasskeyBackupGenerationGrant,
    ): PasskeyBackupHeadDescriptor {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        requireSession(session)
        grant.requireFor(session, metadata, nowSeconds())
        val response =
            execute(
                session,
                metadata,
                COMMIT_PATH,
                "grant.",
                grant.token,
                metadata.body(),
                mapOf("X-Passkey-Owner-Session" to session.sessionToken),
            )
        grant.requireFor(session, metadata, nowSeconds())
        return OwnerGenerationResponseParser.status(response.body, metadata, allowAbsent = false).let {
            (it as PasskeyBackupGenerationOperationStatus.Committed).descriptor
        }
    }

    /** Use after an unknown commit outcome; absence never permits an automatic second upload. */
    suspend fun operationStatus(
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
    ): PasskeyBackupGenerationOperationStatus {
        val body =
            JsonObject().apply {
                addProperty("schemaVersion", 1)
                addProperty("operationId", metadata.operationId)
            }.toString().toByteArray(Charsets.UTF_8)
        val response = execute(session, metadata, OPERATION_PATH, "session.", session.sessionToken, body)
        return OwnerGenerationResponseParser.status(response.body, metadata, allowAbsent = true)
    }

    private suspend fun execute(
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
        path: String,
        bearerPrefix: String,
        bearerToken: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ): GoogleDriveHttpResponse {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        requireSession(session)
        PasskeyBackupGenerationFormat.requireIdentifier(bearerToken, bearerPrefix)
        currentCoroutineContext().ensureActive()
        val selectedSubject = tokenProvider.accessToken().subject
        currentCoroutineContext().ensureActive()
        metadata.requireScope(session, PasskeyBackupGenerationFormat.storageAccountBinding(selectedSubject))
        val response =
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "POST",
                    url = "$normalizedBaseUrl$path",
                    headers =
                        mapOf(
                            "Authorization" to "Bearer $bearerToken",
                            "Content-Type" to "application/json; charset=utf-8",
                            "Cache-Control" to "no-store",
                        ) + extraHeaders,
                    body = body,
                    maxResponseBytes = MAX_RESPONSE_BYTES,
                ),
            )
        currentCoroutineContext().ensureActive()
        require(response.code == HTTP_OK) { "Owner backup metadata request failed with HTTP ${response.code}" }
        requireSession(session)
        require(tokenProvider.accessToken().subject == selectedSubject) { "Google Drive selected account changed" }
        currentCoroutineContext().ensureActive()
        requireSession(session)
        return response
    }

    private fun requireSession(session: PasskeyBackupOwnerSession) {
        PasskeyBackupGenerationFormat.requireIdentifier(session.sessionToken, "session.")
        PasskeyBackupGenerationFormat.requireIdentifier(session.subject, "owner:")
        PasskeyBackupGenerationFormat.requireIdentifier(session.namespace, "backup:")
        require(session.platform == "android" && session.generation >= 0) { "Invalid owner session" }
        val current = nowSeconds()
        require(session.expiresAt > current && session.expiresAt <= current + MAX_SESSION_AHEAD_SECONDS) {
            "Owner session is expired or has an invalid lifetime"
        }
    }

    private fun nowSeconds(): Long {
        val currentMillis = nowMillis()
        require(currentMillis >= 0) { "Invalid owner session clock" }
        return currentMillis / MILLIS_PER_SECOND
    }

    private companion object {
        const val GRANT_PATH = "/api/passkey-backup/v1/owner/backup/grant"
        const val COMMIT_PATH = "/api/passkey-backup/v1/owner/backup/commit"
        const val OPERATION_PATH = "/api/passkey-backup/v1/owner/backup/operation"
        const val HTTP_OK = 200
        const val MAX_RESPONSE_BYTES = 8 * 1024
        const val MAX_SESSION_AHEAD_SECONDS = 660L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** Duplicate-key and type-safe closed JSON parser for public owner metadata responses. */
private object OwnerGenerationResponseParser {
    private const val MAX_RESPONSE_BYTES = 8 * 1024
    private const val MAX_FIELDS = 12
    private const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L
    private const val MAX_GRANT_AHEAD_SECONDS = 65L
    private val DECIMAL = Regex("^(0|[1-9][0-9]*)$")
    private val DESCRIPTOR_FIELDS =
        setOf(
            "headRevision",
            "parentHeadRevision",
            "parentHeadSha256",
            "generationId",
            "bundleSha256",
            "keyEpoch",
            "driveFileId",
            "storageAccountBinding",
        )

    fun grant(
        bytes: ByteArray,
        session: PasskeyBackupOwnerSession,
        metadata: PasskeyBackupGenerationMetadata,
        nowSeconds: Long,
    ): PasskeyBackupGenerationGrant = parseSafely(bytes) { root ->
            require(root.keySet() == setOf("token", "expiresAt"))
            val token = root.string("token")
            PasskeyBackupGenerationFormat.requireIdentifier(token, "grant.")
            val expiresAt = decimal(root.number("expiresAt"))
            require(
                expiresAt > nowSeconds && expiresAt <= nowSeconds + MAX_GRANT_AHEAD_SECONDS &&
                    expiresAt <= session.expiresAt,
            ) { "Invalid generation grant expiry" }
            PasskeyBackupGenerationGrant(token, expiresAt, session.sessionToken, metadata.digest())
        }

    fun status(
        bytes: ByteArray,
        metadata: PasskeyBackupGenerationMetadata,
        allowAbsent: Boolean,
    ): PasskeyBackupGenerationOperationStatus = parseSafely(bytes) { root ->
            when (root.string("status")) {
                "absent" -> {
                    require(allowAbsent && root.keySet() == setOf("status"))
                    PasskeyBackupGenerationOperationStatus.Absent
                }
                "committed" -> {
                    require(root.keySet() == setOf("status", "descriptor"))
                    val descriptorObject = root.get("descriptor")
                    require(descriptorObject != null && descriptorObject.isJsonObject)
                    val descriptor = descriptor(descriptorObject.asJsonObject)
                    metadata.requireDescriptor(descriptor)
                    PasskeyBackupGenerationOperationStatus.Committed(descriptor)
                }
                else -> error("Invalid owner backup status")
            }
        }

    private fun descriptor(value: JsonObject): PasskeyBackupHeadDescriptor {
        require(value.keySet() == DESCRIPTOR_FIELDS)
        val parentValue = value.get("parentHeadSha256")
        val parentDigest = if (parentValue?.isJsonNull == true) null else value.string("parentHeadSha256")
        return PasskeyBackupHeadDescriptor(
            headRevision = decimal(value.string("headRevision")),
            parentHeadRevision = decimal(value.string("parentHeadRevision")),
            parentHeadSha256 = parentDigest,
            generationId = value.string("generationId"),
            bundleSha256 = value.string("bundleSha256"),
            keyEpoch = decimal(value.string("keyEpoch")),
            driveFileId = value.string("driveFileId"),
            storageAccountBinding = value.string("storageAccountBinding"),
        )
    }

    private fun <T> parseSafely(bytes: ByteArray, parse: (JsonObject) -> T): T = try {
        require(bytes.size in 1..MAX_RESPONSE_BYTES)
        val text =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            readObject(reader, depth = 0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        parse(JsonParser.parseString(text).asJsonObject)
    } catch (_: Exception) {
        throw IllegalArgumentException("Owner backup metadata response is malformed")
    }

    private fun readObject(reader: JsonReader, depth: Int) {
        require(depth <= 1 && reader.peek() == JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        val seen = mutableSetOf<String>()
        while (reader.hasNext()) {
            require(seen.add(reader.nextName()) && seen.size <= MAX_FIELDS)
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.NULL -> reader.nextNull()
                JsonToken.BEGIN_OBJECT -> {
                    require(depth == 0)
                    readObject(reader, depth + 1)
                }
                else -> error("Unexpected owner backup metadata JSON type")
            }
        }
        reader.endObject()
    }

    private fun decimal(value: String): Long {
        require(DECIMAL.matches(value))
        return value.toLongOrNull()?.takeIf { it <= MAX_SAFE_JS_INTEGER }
            ?: throw IllegalArgumentException("Invalid owner backup integer")
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
