package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Append-only storage primitive. It cannot change an authoritative head, PATCH, delete or decrypt. */
class GoogleDrivePasskeyBackupGenerationStorage(
    accountSubject: String,
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(
        OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    )
) {
    private val accountSubject = GoogleDriveVerifiedIdentity.requireSubject(accountSubject)
    private val accountBinding = PasskeyBackupGenerationFormat.storageAccountBinding(accountSubject)

    /** Losing this response is harmless: it allocates an ID without creating ciphertext. */
    suspend fun allocateFileId(): String {
        val response = execute(
            "GET", "$BASE_URL/generateIds?count=1&space=appDataFolder&type=files", maximum = METADATA_BYTES
        )
        require(response.code == HTTP_OK) { "Drive generation ID allocation failed" }
        return GoogleDriveGenerationResponse.allocatedId(response.body)
    }

    /** Persist this ID, exact bytes, digest and owner operation ID before attempting [createCandidate]. */
    fun prepareCandidate(fileId: String, generation: PasskeyBackupGeneration): Candidate {
        require(generation.context.storageAccountBinding == accountBinding) { "Generation storage account mismatch" }
        return Candidate(fileId, generation.context, PasskeyBackupGenerationFormat.encode(generation))
    }

    /**
     * Even ACKNOWLEDGED requires a separate download, local decryption and owner-authorized CAS.
     * Cancellation or any thrown error after POST admission also leaves an unknown upload outcome.
     * Retry only the same journaled candidate; this method never retries or allocates a replacement ID.
     */
    suspend fun createCandidate(candidate: Candidate): CreateOutcome {
        require(candidate.context.storageAccountBinding == accountBinding) { "Generation storage account mismatch" }
        val metadata = JsonObject().apply {
            addProperty("id", candidate.fileId)
            addProperty("name", fileName(candidate.context))
            addProperty("mimeType", GoogleDrivePasskeyBackup.MIME_TYPE)
            add("parents", JsonArray().apply { add(GoogleDrivePasskeyBackup.APP_DATA_FOLDER) })
            add("appProperties", properties(candidate.context, candidate.sha256))
        }
        val boundary = "fearless-generation-${UUID.randomUUID()}"
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toString().toByteArray(Charsets.UTF_8))
            write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(candidate.bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val request = authorize(
            "POST", "$UPLOAD_URL?uploadType=multipart&fields=${encoded(FIELDS)}",
            mapOf("Content-Type" to "multipart/related; boundary=$boundary"), body, METADATA_BYTES
        )
        val response = try {
            transport.execute(request)
        } catch (_: IOException) {
            return CreateOutcome.RECONCILE_REQUIRED
        }
        currentCoroutineContext().ensureActive()
        if (response.code != HTTP_OK && response.code != HTTP_CREATED) return CreateOutcome.RECONCILE_REQUIRED
        val acknowledged = runCatching {
            GoogleDriveGenerationResponse.metadata(
                response.body, candidate.fileId, fileName(candidate.context), properties(candidate.context, candidate.sha256)
            ).also { require(it == candidate.size) }
        }.isSuccess
        return if (acknowledged) CreateOutcome.ACKNOWLEDGED else CreateOutcome.RECONCILE_REQUIRED
    }

    /** A null response is an observed 404, not permission to abandon/recreate/delete a generation. */
    suspend fun readCandidate(
        fileId: String,
        expectedContext: PasskeyBackupGeneration.Context,
        expectedSha256: String
    ): PasskeyBackupGeneration? {
        GoogleDriveGenerationResponse.requireFileId(fileId)
        PasskeyBackupGenerationFormat.requireSha256(expectedSha256)
        require(expectedContext.storageAccountBinding == accountBinding) { "Generation storage account mismatch" }
        val url = "$BASE_URL/$fileId"
        val metadata = execute("GET", "$url?fields=${encoded(FIELDS)}", maximum = METADATA_BYTES)
        if (metadata.code == HTTP_NOT_FOUND) return null
        require(metadata.code == HTTP_OK) { "Drive generation metadata unavailable" }
        val size = GoogleDriveGenerationResponse.metadata(
            metadata.body, fileId, fileName(expectedContext), properties(expectedContext, expectedSha256)
        )
        val response = execute("GET", "$url?alt=media", maximum = size)
        if (response.code == HTTP_NOT_FOUND) return null
        require(response.code == HTTP_OK && response.body.size == size) { "Drive generation content unavailable" }
        return PasskeyBackupGenerationFormat.decode(response.body, expectedContext, expectedSha256)
    }

    private suspend fun execute(
        method: String,
        url: String,
        maximum: Int
    ): GoogleDriveHttpResponse {
        val response = transport.execute(authorize(method, url, maximum = maximum))
        currentCoroutineContext().ensureActive()
        require(response.body.size <= maximum) { "Drive generation response too large" }
        return response
    }

    private suspend fun authorize(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        maximum: Int
    ): GoogleDriveHttpRequest {
        currentCoroutineContext().ensureActive()
        val access = tokenProvider.accessToken()
        currentCoroutineContext().ensureActive()
        require(access.subject == accountSubject) { "Drive generation account changed" }
        return GoogleDriveHttpRequest(
            method, url, headers + mapOf("Authorization" to "Bearer ${access.accessToken}", "Cache-Control" to "no-store"),
            body, isOneShot = body != null, maxResponseBytes = maximum
        )
    }

    class Candidate internal constructor(
        val fileId: String,
        val context: PasskeyBackupGeneration.Context,
        bytes: ByteArray
    ) {
        private val content = bytes.copyOf()
        val bytes: ByteArray get() = content.copyOf()
        val size: Int get() = content.size
        val sha256: String = PasskeyBackupGenerationFormat.sha256(content)

        init {
            GoogleDriveGenerationResponse.requireFileId(fileId)
            require(content.size in 1..PasskeyBackupGenerationFormat.MAX_BYTES) { "Invalid generation size" }
        }

        override fun toString(): String = "DriveGenerationCandidate(redacted)"
    }

    enum class CreateOutcome { ACKNOWLEDGED, RECONCILE_REQUIRED }

    private companion object {
        const val BASE_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FIELDS = "id,name,mimeType,spaces,appProperties,size"
        const val REQUEST_TIMEOUT_SECONDS = 30L
        const val METADATA_BYTES = 8192
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NOT_FOUND = 404

        fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8")
        fun fileName(context: PasskeyBackupGeneration.Context): String =
            "fearless-passkey-generation-${context.generationId}.bin"
        fun properties(context: PasskeyBackupGeneration.Context, digest: String): JsonObject = JsonObject().apply {
            addProperty("format", "FPBKGEN1")
            addProperty("namespaceSha256", PasskeyBackupGenerationFormat.sha256(context.backupNamespace.toByteArray(Charsets.UTF_8)))
            addProperty("generationId", context.generationId)
            addProperty("bundleSha256", digest)
        }
    }
}
