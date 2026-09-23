package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

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
    internal const val MAX_HTTP_RESPONSE_BYTES = 256 * 1024
    internal const val MAX_LIST_PAGES = 20
    internal const val MAX_NEXT_PAGE_TOKEN_LENGTH = 2048
    internal const val MAX_APP_PROPERTY_BYTES = 124

    private const val MAX_ACCOUNT_NAME_LENGTH = 320
    private val accountNamePattern = Regex("^[^\\s@]+@[^\\s@]+$")

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
        require(accountNamePattern.matches(normalized)) {
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

class GoogleDriveAccountAccess(accountName: String, accessToken: String) {
    val accountName = GoogleDrivePasskeyBackup.requireAccountName(accountName)
    val accessToken = accessToken.trim().also { token ->
        require(token.length in 1..4096 && BEARER_TOKEN.matches(token)) {
            "Google Drive access token is invalid"
        }
    }

    override fun toString(): String = "GoogleDriveAccountAccess(accountName=[REDACTED], accessToken=[REDACTED])"

    private companion object {
        val BEARER_TOKEN = Regex("^[A-Za-z0-9._~+/\\-]+={0,}$")
    }
}

fun interface GoogleDriveAccessTokenProvider {
    suspend fun accessToken(): GoogleDriveAccountAccess
}

data class GoogleDriveHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val isOneShot: Boolean = false,
    val maxResponseBytes: Int = GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES
) {
    init {
        require(maxResponseBytes > 0 && maxResponseBytes <= GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES) {
            "Google Drive HTTP response limit must be 1-${GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES} bytes"
        }
    }

    override fun toString(): String =
        "GoogleDriveHttpRequest(method=$method, url=$url, headerNames=${headers.keys}, bodyBytes=${body?.size}, isOneShot=$isOneShot)"
}

data class GoogleDriveHttpResponse(
    val code: Int,
    val body: ByteArray = ByteArray(0)
)

interface GoogleDriveHttpTransport {
    suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse
}

class OkHttpGoogleDriveHttpTransport(
    okHttpClient: OkHttpClient
) : GoogleDriveHttpTransport {
    private val okHttpClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        val mediaType = request.contentType()?.toMediaType()
        val requestBody = request.body?.let { body ->
            if (request.isOneShot) {
                OneShotByteArrayRequestBody(body, mediaType)
            } else {
                body.toRequestBody(mediaType)
            }
        }
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

        return okHttpClient.newCall(builder.build()).awaitResponse(request.maxResponseBytes)
    }

    private fun GoogleDriveHttpRequest.contentType(): String? {
        return headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
    }

    private suspend fun Call.awaitResponse(maxResponseBytes: Int): GoogleDriveHttpResponse {
        return suspendCancellableCoroutine { continuation ->
            val callbackCompleted = AtomicBoolean(false)
            continuation.invokeOnCancellation {
                callbackCompleted.set(true)
                cancel()
            }

            if (continuation.isActive) {
                enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, exception: IOException) {
                            if (callbackCompleted.compareAndSet(false, true)) {
                                continuation.resumeWith(Result.failure(exception))
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val result = runCatching {
                                response.use {
                                    GoogleDriveHttpResponse(
                                        code = it.code,
                                        body = it.readBoundedBody(maxResponseBytes)
                                    )
                                }
                            }
                            if (callbackCompleted.compareAndSet(false, true)) {
                                continuation.resumeWith(result)
                            }
                        }
                    }
                )
            } else {
                cancel()
            }
        }
    }

    private fun Response.readBoundedBody(maxResponseBytes: Int): ByteArray {
        val responseBody = body ?: return ByteArray(0)
        val declaredLength = responseBody.contentLength()
        if (declaredLength > maxResponseBytes) {
            throw IOException("HTTP response exceeds the $maxResponseBytes byte limit")
        }
        val initialCapacity = if (declaredLength > 0) {
            declaredLength.toInt()
        } else {
            minOf(RESPONSE_READ_BUFFER_BYTES, maxResponseBytes)
        }
        return responseBody.byteStream().readBoundedBytes(maxResponseBytes, initialCapacity)
    }

    private fun InputStream.readBoundedBytes(maxResponseBytes: Int, initialCapacity: Int): ByteArray {
        val output = ByteArrayOutputStream(initialCapacity)
        val readBuffer = ByteArray(RESPONSE_READ_BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val readBytes = read(readBuffer)
            if (readBytes == -1) break
            totalBytes += readBytes
            if (totalBytes > maxResponseBytes) {
                throw IOException("HTTP response exceeds the $maxResponseBytes byte limit")
            }
            output.write(readBuffer, 0, readBytes)
        }
        return output.toByteArray()
    }

    private class OneShotByteArrayRequestBody(
        body: ByteArray,
        private val mediaType: MediaType?
    ) : RequestBody() {
        private val body = body.copyOf()

        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = body.size.toLong()

        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            sink.write(body)
        }
    }

    private companion object {
        const val RESPONSE_READ_BUFFER_BYTES = 8 * 1024
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
        val metadata = metadataJson(normalizedPayload).toString()
        val access = accessTokenProvider.accessToken()
        GoogleDrivePasskeyBackup.requireMatchingAccountName(
            expected = normalizedPayload.accountName,
            actual = access.accountName,
            ceremony = "backup upload"
        )
        val existingFile = findBackupFile(normalizedPayload.storageKey, access)
        existingFile?.let { file ->
            GoogleDrivePasskeyBackup.requireMatchingAccountName(
                expected = access.accountName,
                actual = file.accountName,
                ceremony = "backup replacement"
            )
            require(file.walletId == normalizedPayload.walletId) {
                "Google Drive passkey backup walletId mismatch before replacement"
            }
        }
        val boundary = "fearless-passkey-backup-${normalizedPayload.storageKey}"
        val requestBody = multipartBody(
            boundary = boundary,
            metadata = metadata,
            encryptedPayload = normalizedPayload.encryptedPayload
        )

        val request = if (existingFile == null) {
            authenticatedRequest(
                access = access,
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
                access = access,
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
        val access = accessTokenProvider.accessToken()
        val file = findBackupFile(normalizedStorageKey, access) ?: return null

        GoogleDrivePasskeyBackup.requireMatchingAccountName(
            expected = access.accountName,
            actual = file.accountName,
            ceremony = "backup download"
        )

        require(file.schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: ${file.schemaVersion}"
        }

        val response = transport.execute(
            authenticatedRequest(
                access = access,
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
        val access = accessTokenProvider.accessToken()
        val file = findBackupFile(normalizedStorageKey, access) ?: return

        GoogleDrivePasskeyBackup.requireMatchingAccountName(
            expected = access.accountName,
            actual = file.accountName,
            ceremony = "backup deletion"
        )

        val response = transport.execute(
            authenticatedRequest(
                access = access,
                method = "DELETE",
                url = driveUrl(path = "/files/${encodePathSegment(file.id)}")
            )
        )

        if (response.code != GoogleDrivePasskeyBackup.HTTP_NOT_FOUND) {
            requireSuccess(response, "delete")
        }
    }

    private suspend fun findBackupFile(
        storageKey: String,
        access: GoogleDriveAccountAccess
    ): GoogleDrivePasskeyBackupFile? {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val seenPageTokens = mutableSetOf<String>()
        var pageToken: String? = null
        var match: GoogleDrivePasskeyBackupFile? = null
        repeat(GoogleDrivePasskeyBackup.MAX_LIST_PAGES) {
            val query = mutableMapOf(
                "spaces" to GoogleDrivePasskeyBackup.APP_DATA_FOLDER,
                "pageSize" to "100",
                "fields" to "nextPageToken,incompleteSearch,files(id,name,appProperties)",
                "q" to "name = '${fileName(normalizedStorageKey)}' and trashed = false"
            )
            pageToken?.let { query["pageToken"] = it }
            val response = transport.execute(
                authenticatedRequest(
                    access = access,
                    method = "GET",
                    url = driveUrl(path = "/files", query = query)
                )
            )
            requireSuccess(response, "list")
            val page = parseFileList(response.bodyText())
            require(!page.incompleteSearch) { "Google Drive passkey backup search was incomplete" }
            page.files.forEach { file ->
                require(file.name == fileName(normalizedStorageKey) && file.storageKey == normalizedStorageKey) {
                    "Google Drive passkey backup file identity mismatch"
                }
                require(match == null) {
                    "Multiple passkey backup files found for storage key: $normalizedStorageKey"
                }
                match = file
            }
            val next = page.nextPageToken ?: return match
            require(seenPageTokens.add(next)) { "Google Drive passkey backup list repeated a page token" }
            pageToken = next
        }
        error("Google Drive passkey backup list exceeded the page limit")
    }

    private suspend fun authenticatedRequest(
        access: GoogleDriveAccountAccess,
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): GoogleDriveHttpRequest {
        return GoogleDriveHttpRequest(
            method = method,
            url = url,
            headers = headers + mapOf("Authorization" to "Bearer ${access.accessToken}"),
            body = body
        )
    }

    private fun metadataJson(payload: PasskeyBackupEncryptedPayload): JsonObject {
        val parents = JsonArray().apply {
            add(GoogleDrivePasskeyBackup.APP_DATA_FOLDER)
        }
        val appProperties = JsonObject().apply {
            addBoundedProperty("storageKey", payload.storageKey)
            addBoundedProperty("walletId", payload.walletId)
            addBoundedProperty("accountName", payload.accountName)
            addBoundedProperty("createdAtMillis", payload.createdAtMillis.toString())
            addBoundedProperty("schemaVersion", payload.schemaVersion.toString())
        }

        return JsonObject().apply {
            addProperty("name", fileName(payload.storageKey))
            addProperty("mimeType", GoogleDrivePasskeyBackup.MIME_TYPE)
            add("parents", parents)
            add("appProperties", appProperties)
        }
    }

    private fun JsonObject.addBoundedProperty(name: String, value: String) {
        require((name + value).toByteArray(StandardCharsets.UTF_8).size <= GoogleDrivePasskeyBackup.MAX_APP_PROPERTY_BYTES) {
            "Google Drive passkey backup appProperties.$name exceeds the Drive byte limit"
        }
        addProperty(name, value)
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

    private fun parseFileList(body: String): GoogleDrivePasskeyBackupPage {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
            error("Malformed Google Drive passkey backup file list")
        }
        val files = checkNotNull(root.getAsJsonArray("files")) {
            "Malformed Google Drive passkey backup file list"
        }

        val incompleteSearch = root.get("incompleteSearch")?.let { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
                "Malformed Google Drive passkey backup incompleteSearch"
            }
            value.asBoolean
        } ?: false
        val nextPageToken = root.get("nextPageToken")?.let { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "Malformed Google Drive passkey backup nextPageToken"
            }
            value.asString.also { token ->
                require(
                    token.isNotEmpty() &&
                    token.length <= GoogleDrivePasskeyBackup.MAX_NEXT_PAGE_TOKEN_LENGTH &&
                    token.none { it.isISOControl() }
                ) {
                    "Invalid Google Drive passkey backup nextPageToken"
                }
            }
        }

        val parsedFiles = files.map { element ->
            val file = element.asJsonObject
            val id = file.get("id")?.asString?.trim().orEmpty()
            require(id.isNotEmpty()) { "Google Drive passkey backup file id is required" }
            val name = file.get("name")?.asString?.trim().orEmpty()
            require(name.isNotEmpty()) { "Google Drive passkey backup file name is required" }

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
                name = name,
                storageKey = storageKey,
                walletId = walletId,
                accountName = accountName,
                createdAtMillis = createdAtMillis,
                schemaVersion = schemaVersion
            )
        }
        return GoogleDrivePasskeyBackupPage(parsedFiles, nextPageToken, incompleteSearch)
    }

    private fun requiredAppProperty(appProperties: JsonObject, name: String): String {
        val value = appProperties.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Google Drive passkey backup appProperties.$name is required"
        }
        return value.asString.also { text ->
            require((name + text).toByteArray(StandardCharsets.UTF_8).size <= GoogleDrivePasskeyBackup.MAX_APP_PROPERTY_BYTES) {
                "Google Drive passkey backup appProperties.$name exceeds the Drive byte limit"
            }
        }
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
    val name: String,
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val createdAtMillis: Long,
    val schemaVersion: Int
)

private data class GoogleDrivePasskeyBackupPage(
    val files: List<GoogleDrivePasskeyBackupFile>,
    val nextPageToken: String?,
    val incompleteSearch: Boolean
)
