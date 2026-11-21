package jp.co.soramitsu.backup.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.oauth.GoogleDriveAccessTokenSource
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import jp.co.soramitsu.backup.passkey.GoogleDrivePasskeyBackup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A Drive v3 error response, reduced to its status and machine-readable reasons; no body text. */
class GoogleDriveHttpException(
    val statusCode: Int,
    val reasons: Set<String>
) : IOException("Google Drive request failed with HTTP $statusCode ${reasons.sorted()}") {

    /** The access token lacks a scope, for example because the user turned Drive access off. */
    val isInsufficientScope: Boolean
        get() = statusCode == HTTP_FORBIDDEN && reasons.any { it in INSUFFICIENT_SCOPE_REASONS }

    fun isForbiddenBecause(reason: String): Boolean = statusCode == HTTP_FORBIDDEN && reason in reasons

    internal companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        val INSUFFICIENT_SCOPE_REASONS = setOf("insufficientPermissions", "insufficientScopes", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")
    }
}

/** One `files` entry, with only the fields a listing asked for. */
internal data class GoogleDriveFile(
    val id: String,
    val name: String?,
    val description: String?,
    val modifiedTimeMillis: Long?
)

internal class GoogleDriveFilePage(val files: List<GoogleDriveFile>, val nextPageToken: String?)

/**
 * The few Drive v3 REST calls wallet backup makes, over the shared bounded OkHttp transport. Every
 * request carries a bearer token from [tokens]; a 401 drops that token and is retried once with a
 * fresh one, and a second 401 means the user must sign in again.
 */
internal class GoogleDriveApi(
    private val transport: GoogleDriveHttpTransport,
    private val tokens: GoogleDriveAccessTokenSource,
    private val random: SecureRandom = SecureRandom()
) {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    suspend fun listFiles(
        query: String,
        fields: String,
        spaces: String,
        pageToken: String?
    ): GoogleDriveFilePage {
        val url = DRIVE_FILES_URL.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", spaces)
            .addQueryParameter("fields", fields)
            .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .build()
        val response = execute("GET", url)
        return parsePage(response)
    }

    suspend fun downloadFile(fileId: String): ByteArray {
        val url = DRIVE_FILES_URL.newBuilder()
            .addPathSegment(requireFileId(fileId))
            .addQueryParameter("alt", "media")
            .build()
        return execute("GET", url).body
    }

    /** Metadata-only create, used for folders. Returns the new file's id. */
    suspend fun createMetadata(metadata: JsonObject, fields: String): String {
        val url = DRIVE_FILES_URL.newBuilder().addQueryParameter("fields", fields).build()
        val response = execute(
            method = "POST",
            url = url,
            contentType = JSON_CONTENT_TYPE,
            body = gson.toJson(metadata).toByteArray(StandardCharsets.UTF_8)
        )
        return parseCreatedId(response)
    }

    /** A multipart/related upload: the JSON metadata, then the media with [mimeType]. Returns the new file's id. */
    suspend fun createMultipart(
        metadata: JsonObject,
        mimeType: String,
        content: ByteArray,
        fields: String
    ): String {
        val url = DRIVE_UPLOAD_URL.newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", fields)
            .build()
        val boundary = newBoundary(content)
        val response = execute(
            method = "POST",
            url = url,
            contentType = "multipart/related; boundary=$boundary",
            body = multipartBody(boundary, gson.toJson(metadata), mimeType, content)
        )
        return parseCreatedId(response)
    }

    suspend fun deleteFile(fileId: String) {
        val url = DRIVE_FILES_URL.newBuilder().addPathSegment(requireFileId(fileId)).build()
        execute("DELETE", url)
    }

    private suspend fun execute(
        method: String,
        url: HttpUrl,
        contentType: String? = null,
        body: ByteArray? = null
    ): GoogleDriveHttpResponse {
        val firstToken = tokens.accessToken()
        val first = transport.execute(request(method, url, firstToken, contentType, body))
        if (first.code != GoogleDriveHttpException.HTTP_UNAUTHORIZED) return requireSuccess(first)

        tokens.onAccessTokenRejected(firstToken)
        val secondToken = tokens.accessToken()
        val second = transport.execute(request(method, url, secondToken, contentType, body))
        if (second.code == GoogleDriveHttpException.HTTP_UNAUTHORIZED) {
            tokens.onAccessTokenRejected(secondToken)
            throw UnauthorizedException(driveError(second))
        }
        return requireSuccess(second)
    }

    private fun request(
        method: String,
        url: HttpUrl,
        token: String,
        contentType: String?,
        body: ByteArray?
    ): GoogleDriveHttpRequest {
        val headers = buildMap {
            put("Authorization", "Bearer $token")
            put("Accept", "application/json")
            contentType?.let { put("Content-Type", it) }
        }
        return GoogleDriveHttpRequest(
            method = method,
            url = url.toString(),
            headers = headers,
            body = body,
            isOneShot = body != null,
            maxResponseBytes = MAX_RESPONSE_BYTES
        )
    }

    private fun requireSuccess(response: GoogleDriveHttpResponse): GoogleDriveHttpResponse {
        if (response.code !in HTTP_SUCCESS) throw driveError(response)
        return response
    }

    private fun driveError(response: GoogleDriveHttpResponse): GoogleDriveHttpException {
        val error = parseObject(response.body)?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        val reasons = listOfNotNull(error?.get("errors"), error?.get("details"))
            .filter { it.isJsonArray }
            .flatMap { it.asJsonArray.reasons() }
            .toSet()
        return GoogleDriveHttpException(response.code, reasons)
    }

    private fun JsonArray.reasons(): List<String> = mapNotNull { entry ->
        entry.takeIf { it.isJsonObject }?.asJsonObject?.string("reason")?.takeIf { REASON.matches(it) }
    }

    private fun parsePage(response: GoogleDriveHttpResponse): GoogleDriveFilePage {
        val json = parseObject(response.body) ?: throw MalformedDriveResponseException()
        val files = json.get("files")?.requireArray()?.map(::parseFile).orEmpty()
        val nextPageToken = json.get("nextPageToken")?.let(::parsePageToken)
        return GoogleDriveFilePage(files, nextPageToken)
    }

    private fun parsePageToken(element: JsonElement): String =
        element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_PAGE_TOKEN_LENGTH && it.none(Char::isISOControl) }
            ?: throw MalformedDriveResponseException()

    private fun JsonElement.requireArray(): JsonArray =
        takeIf { it.isJsonArray }?.asJsonArray ?: throw MalformedDriveResponseException()

    private fun parseFile(element: JsonElement): GoogleDriveFile {
        val file = element.takeIf { it.isJsonObject }?.asJsonObject ?: throw MalformedDriveResponseException()
        val id = file.string("id")?.takeIf(::isFileId) ?: throw MalformedDriveResponseException()
        return GoogleDriveFile(
            id = id,
            name = file.string("name"),
            description = file.string("description"),
            modifiedTimeMillis = file.string("modifiedTime")?.let(::parseRfc3339Millis)
        )
    }

    private fun parseCreatedId(response: GoogleDriveHttpResponse): String =
        parseObject(response.body)?.string("id")?.takeIf(::isFileId) ?: throw MalformedDriveResponseException()

    private fun parseObject(body: ByteArray): JsonObject? = runCatching {
        JsonParser.parseString(body.toString(StandardCharsets.UTF_8)).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun newBoundary(content: ByteArray): String {
        val text = content.toString(StandardCharsets.ISO_8859_1)
        while (true) {
            val boundary = "fearless-backup-" + ByteArray(BOUNDARY_RANDOM_BYTES).also(random::nextBytes)
                .joinToString("") { "%02x".format(it) }
            if (!text.contains(boundary)) return boundary
        }
    }

    private fun multipartBody(
        boundary: String,
        metadata: String,
        mimeType: String,
        content: ByteArray
    ): ByteArray {
        val head = buildString {
            append("--").append(boundary).append(CRLF)
            append("Content-Type: application/json; charset=UTF-8").append(CRLF).append(CRLF)
            append(metadata).append(CRLF)
            append("--").append(boundary).append(CRLF)
            append("Content-Type: ").append(mimeType).append(CRLF).append(CRLF)
        }.toByteArray(StandardCharsets.UTF_8)
        val tail = "$CRLF--$boundary--$CRLF".toByteArray(StandardCharsets.UTF_8)
        return head + content + tail
    }

    private fun requireFileId(fileId: String): String {
        require(isFileId(fileId)) { "Invalid Google Drive file id" }
        return fileId
    }

    private fun isFileId(value: String): Boolean = FILE_ID.matches(value)

    /** Drive answered 2xx with a body this client cannot read. */
    class MalformedDriveResponseException : IOException("Malformed Google Drive response")

    companion object {
        val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files".toHttpUrl()
        val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl()
        private const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
        private const val CRLF = "\r\n"
        private const val BOUNDARY_RANDOM_BYTES = 16
        private const val MAX_PAGE_TOKEN_LENGTH = 4096

        // The largest response the shared transport allows; backups and listing pages are far smaller.
        private const val MAX_RESPONSE_BYTES = GoogleDrivePasskeyBackup.MAX_GENERATION_HTTP_RESPONSE_BYTES
        private val HTTP_SUCCESS = 200..299
        private val FILE_ID = Regex("^[A-Za-z0-9_-]{1,256}$")
        private val REASON = Regex("^[A-Za-z0-9_.-]{1,128}$")

        /** RFC 3339 as Drive writes it; null for anything else, which only affects ordering. */
        fun parseRfc3339Millis(value: String): Long? = try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (ignored: DateTimeParseException) {
            null
        }
    }
}
