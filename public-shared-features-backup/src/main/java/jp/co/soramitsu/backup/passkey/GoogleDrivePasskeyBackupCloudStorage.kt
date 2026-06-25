package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object GoogleDrivePasskeyBackup {
    const val APP_DATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val OAUTH_APP_DATA_SCOPE = "oauth2:$APP_DATA_SCOPE"
    const val APP_DATA_FOLDER = "appDataFolder"
    const val MIME_TYPE = "application/octet-stream"

    internal const val HTTP_SUCCESS_MIN = 200
    internal const val HTTP_SUCCESS_MAX = 299
    internal const val HTTP_NOT_FOUND = 404
    internal const val DRIVE_BASE_URL = "https://www.googleapis.com/drive/v3"
    internal const val UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3"
    internal const val FILE_NAME_PREFIX = "fearless-passkey-backup-"

    private const val MAX_ACCOUNT_NAME_LENGTH = 320

    fun requireAccountName(accountName: String): String {
        val normalized = accountName.trim()
        require(normalized.isNotEmpty()) {
            "Google account is required for passkey backup Drive access"
        }
        require(normalized.length <= MAX_ACCOUNT_NAME_LENGTH) {
            "Google account for passkey backup is too long"
        }
        require(normalized.none { it.isISOControl() || it.isWhitespace() }) {
            "Google account for passkey backup must not contain whitespace or control characters"
        }
        require(normalized.contains("@")) {
            "Google account for passkey backup must be an email address"
        }
        return normalized
    }

    fun requireMatchingAccountName(
        expected: String,
        actual: String,
        ceremony: String
    ): String {
        val normalizedExpected = requireAccountName(expected)
        val normalizedActual = requireAccountName(actual)
        require(normalizedActual.equals(normalizedExpected, ignoreCase = true)) {
            "Passkey $ceremony returned a mismatched Google account"
        }
        return normalizedExpected
    }
}

fun interface GoogleDriveAccessTokenProvider {
    suspend fun accessToken(): String
}

data class GoogleDriveHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
)

data class GoogleDriveHttpResponse(
    val code: Int,
    val body: ByteArray = ByteArray(0)
)

interface GoogleDriveHttpTransport {
    suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse
}

class OkHttpGoogleDriveHttpTransport(
    private val okHttpClient: OkHttpClient
) : GoogleDriveHttpTransport {
    override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        val requestBody = request.body?.toRequestBody(request.contentType()?.toMediaType())
        val builder = Request.Builder().url(request.url)

        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        when (request.method.uppercase()) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(requestBody ?: ByteArray(0).toRequestBody(null))
            else -> error("Unsupported Google Drive HTTP method: ${request.method}")
        }

        return okHttpClient.newCall(builder.build()).execute().use { response ->
            GoogleDriveHttpResponse(
                code = response.code,
                body = response.body?.bytes() ?: ByteArray(0)
            )
        }
    }

    private fun GoogleDriveHttpRequest.contentType(): String? {
        return headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
    }
}

class GoogleDrivePasskeyBackupCloudStorage(
    private val driveClient: GoogleDrivePasskeyBackupDriveClient
) : PasskeyBackupCloudStorage {
    override suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload) {
        driveClient.saveBackup(payload)
    }

    override suspend fun loadPasskeyBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        return driveClient.loadBackup(normalizedStorageKey)
    }

    override suspend fun deletePasskeyBackup(storageKey: String) {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        driveClient.deleteBackup(normalizedStorageKey)
    }
}

class GoogleDrivePasskeyBackupDriveClient(
    private val accessTokenProvider: GoogleDriveAccessTokenProvider,
    private val transport: GoogleDriveHttpTransport
) {
    suspend fun saveBackup(payload: PasskeyBackupEncryptedPayload) {
        val normalizedPayload = PasskeyBackupEncryptedPayload(
            storageKey = PasskeyBackupContract.requireStorageKey(payload.storageKey),
            walletId = PasskeyBackupContract.requireWalletId(payload.walletId),
            accountName = GoogleDrivePasskeyBackup.requireAccountName(payload.accountName),
            createdAtMillis = PasskeyBackupContract.requireCreatedAtMillis(payload.createdAtMillis),
            encryptedPayload = payload.encryptedPayload,
            schemaVersion = payload.schemaVersion
        )
        val existingFile = findBackupFile(normalizedPayload.storageKey)
        val boundary = "fearless-passkey-backup-${normalizedPayload.storageKey}"
        val requestBody = multipartBody(
            boundary = boundary,
            metadata = metadataJson(normalizedPayload).toString(),
            encryptedPayload = normalizedPayload.encryptedPayload
        )

        val request = if (existingFile == null) {
            authenticatedRequest(
                method = "POST",
                url = uploadUrl(
                    path = "/files",
                    query = mapOf(
                        "uploadType" to "multipart",
                        "fields" to "id,name,appProperties"
                    )
                ),
                headers = mapOf("Content-Type" to "multipart/related; boundary=$boundary"),
                body = requestBody
            )
        } else {
            authenticatedRequest(
                method = "PATCH",
                url = uploadUrl(
                    path = "/files/${encodePathSegment(existingFile.id)}",
                    query = mapOf(
                        "uploadType" to "multipart",
                        "fields" to "id,name,appProperties"
                    )
                ),
                headers = mapOf("Content-Type" to "multipart/related; boundary=$boundary"),
                body = requestBody
            )
        }

        requireSuccess(transport.execute(request), "upload")
    }

    suspend fun loadBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val file = findBackupFile(normalizedStorageKey) ?: return null

        require(file.schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: ${file.schemaVersion}"
        }

        val response = transport.execute(
            authenticatedRequest(
                method = "GET",
                url = driveUrl(
                    path = "/files/${encodePathSegment(file.id)}",
                    query = mapOf("alt" to "media")
                )
            )
        )

        if (response.code == GoogleDrivePasskeyBackup.HTTP_NOT_FOUND) {
            return null
        }

        requireSuccess(response, "download")

        return PasskeyBackupEncryptedPayload(
            storageKey = normalizedStorageKey,
            walletId = file.walletId,
            accountName = file.accountName,
            createdAtMillis = file.createdAtMillis,
            encryptedPayload = response.body,
            schemaVersion = file.schemaVersion
        )
    }

    suspend fun deleteBackup(storageKey: String) {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val file = findBackupFile(normalizedStorageKey) ?: return

        val response = transport.execute(
            authenticatedRequest(
                method = "DELETE",
                url = driveUrl(path = "/files/${encodePathSegment(file.id)}")
            )
        )

        if (response.code != GoogleDrivePasskeyBackup.HTTP_NOT_FOUND) {
            requireSuccess(response, "delete")
        }
    }

    private suspend fun findBackupFile(storageKey: String): GoogleDrivePasskeyBackupFile? {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val response = transport.execute(
            authenticatedRequest(
                method = "GET",
                url = driveUrl(
                    path = "/files",
                    query = mapOf(
                        "spaces" to GoogleDrivePasskeyBackup.APP_DATA_FOLDER,
                        "pageSize" to "10",
                        "fields" to "files(id,name,appProperties)",
                        "q" to "name = '${fileName(normalizedStorageKey)}' and trashed = false"
                    )
                )
            )
        )

        requireSuccess(response, "list")

        val files = parseFileList(response.bodyText())
        files.forEach { file ->
            require(file.storageKey == normalizedStorageKey) {
                "Google Drive passkey backup metadata storageKey mismatch"
            }
        }
        require(files.size <= 1) {
            "Multiple passkey backup files found for storage key: $normalizedStorageKey"
        }

        return files.firstOrNull()
    }

    private suspend fun authenticatedRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): GoogleDriveHttpRequest {
        val accessToken = accessTokenProvider.accessToken().trim()
        require(accessToken.isNotEmpty()) { "Google Drive access token is required" }

        return GoogleDriveHttpRequest(
            method = method,
            url = url,
            headers = headers + mapOf("Authorization" to "Bearer $accessToken"),
            body = body
        )
    }

    private fun metadataJson(payload: PasskeyBackupEncryptedPayload): JsonObject {
        val parents = JsonArray().apply {
            add(GoogleDrivePasskeyBackup.APP_DATA_FOLDER)
        }
        val appProperties = JsonObject().apply {
            addProperty("storageKey", payload.storageKey)
            addProperty("walletId", payload.walletId)
            addProperty("accountName", payload.accountName)
            addProperty("createdAtMillis", payload.createdAtMillis.toString())
            addProperty("schemaVersion", payload.schemaVersion.toString())
        }

        return JsonObject().apply {
            addProperty("name", fileName(payload.storageKey))
            addProperty("mimeType", GoogleDrivePasskeyBackup.MIME_TYPE)
            add("parents", parents)
            add("appProperties", appProperties)
        }
    }

    private fun multipartBody(
        boundary: String,
        metadata: String,
        encryptedPayload: ByteArray
    ): ByteArray {
        val prefix = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: ").append(GoogleDrivePasskeyBackup.MIME_TYPE).append("\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)

        return prefix + encryptedPayload + suffix
    }

    private fun parseFileList(body: String): List<GoogleDrivePasskeyBackupFile> {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
            error("Malformed Google Drive passkey backup file list")
        }
        val files = checkNotNull(root.getAsJsonArray("files")) {
            "Malformed Google Drive passkey backup file list"
        }

        return files.map { element ->
            val file = element.asJsonObject
            val id = file.get("id")?.asString?.trim().orEmpty()
            require(id.isNotEmpty()) { "Google Drive passkey backup file id is required" }

            val appProperties = checkNotNull(file.getAsJsonObject("appProperties")) {
                "Google Drive passkey backup metadata is required"
            }
            val storageKey = PasskeyBackupContract.requireStorageKey(
                requiredAppProperty(appProperties, "storageKey")
            )
            val walletId = PasskeyBackupContract.requireWalletId(
                requiredAppProperty(appProperties, "walletId")
            )
            val accountName = GoogleDrivePasskeyBackup.requireAccountName(
                requiredAppProperty(appProperties, "accountName")
            )
            val createdAtMillis = PasskeyBackupContract.requireCreatedAtMillis(
                requiredAppProperty(appProperties, "createdAtMillis").toLongOrNull()
                    ?: error("Google Drive passkey backup createdAtMillis must be numeric")
            )
            val schemaVersion = requiredAppProperty(appProperties, "schemaVersion").toIntOrNull()
                ?: error("Google Drive passkey backup schemaVersion must be numeric")

            GoogleDrivePasskeyBackupFile(
                id = id,
                storageKey = storageKey,
                walletId = walletId,
                accountName = accountName,
                createdAtMillis = createdAtMillis,
                schemaVersion = schemaVersion
            )
        }
    }

    private fun requiredAppProperty(appProperties: JsonObject, name: String): String {
        val value = appProperties.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Google Drive passkey backup appProperties.$name is required"
        }
        return value.asString
    }

    private fun fileName(storageKey: String): String {
        return "${GoogleDrivePasskeyBackup.FILE_NAME_PREFIX}$storageKey.bin"
    }

    private fun driveUrl(path: String, query: Map<String, String> = emptyMap()): String {
        return buildUrl(GoogleDrivePasskeyBackup.DRIVE_BASE_URL, path, query)
    }

    private fun uploadUrl(path: String, query: Map<String, String>): String {
        return buildUrl(GoogleDrivePasskeyBackup.UPLOAD_BASE_URL, path, query)
    }

    private fun buildUrl(
        baseUrl: String,
        path: String,
        query: Map<String, String>
    ): String {
        val queryString = query.entries.joinToString("&") { (name, value) ->
            "${urlEncode(name)}=${urlEncode(value)}"
        }
        return baseUrl.trimEnd('/') + path + if (queryString.isEmpty()) "" else "?$queryString"
    }

    private fun encodePathSegment(value: String): String {
        return urlEncode(value)
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    private fun GoogleDriveHttpResponse.bodyText(): String {
        return body.toString(StandardCharsets.UTF_8)
    }

    private fun requireSuccess(response: GoogleDriveHttpResponse, operation: String) {
        require(response.code in GoogleDrivePasskeyBackup.HTTP_SUCCESS_MIN..GoogleDrivePasskeyBackup.HTTP_SUCCESS_MAX) {
            "Google Drive passkey backup $operation failed with HTTP ${response.code}"
        }
    }
}

private data class GoogleDrivePasskeyBackupFile(
    val id: String,
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val createdAtMillis: Long,
    val schemaVersion: Int
)
