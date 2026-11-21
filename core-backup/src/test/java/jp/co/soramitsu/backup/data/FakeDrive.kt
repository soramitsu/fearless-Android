package jp.co.soramitsu.backup.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.backup.oauth.GoogleDriveAccessTokenSource
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.charset.StandardCharsets

/** One stored Drive file, as Drive v3 describes it. */
internal data class StoredDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val parents: List<String>,
    val description: String? = null,
    val content: ByteArray = ByteArray(0),
    val modifiedTime: String? = null
)

/**
 * An in-memory Drive v3 for the requests wallet backup makes: the three listing queries, media
 * download, metadata and multipart create, and delete. Pages hold [pageSize] files.
 */
internal class FakeDrive(private val pageSize: Int = 100) : GoogleDriveHttpTransport {
    val files = linkedMapOf<String, StoredDriveFile>()
    val requests = mutableListOf<GoogleDriveHttpRequest>()
    var acceptedToken = "ya29.synthetic"
    var override: ((GoogleDriveHttpRequest) -> GoogleDriveHttpResponse?)? = null
    private var nextId = 1

    fun add(file: StoredDriveFile) {
        files[file.id] = file
    }

    override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        requests += request
        override?.invoke(request)?.let { return it }
        if (request.headers["Authorization"] != "Bearer $acceptedToken") return error(401, "authError")
        return route(request)
    }

    private fun route(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        val url = request.url.toHttpUrl()
        val path = url.encodedPath
        val isFilePath = path.startsWith("/drive/v3/files/")
        val fileId = path.substringAfterLast('/')
        return when {
            request.method == "GET" && path == "/drive/v3/files" -> list(url.queryParameter("q").orEmpty(), url.queryParameter("pageToken"))
            request.method == "GET" && isFilePath && url.queryParameter("alt") == "media" -> download(fileId)
            request.method == "POST" && path == "/drive/v3/files" -> createFolder(request)
            request.method == "POST" && path == "/upload/drive/v3/files" -> createMultipart(request)
            request.method == "DELETE" && isFilePath -> delete(fileId)
            else -> error(400, "badRequest")
        }
    }

    private fun download(fileId: String): GoogleDriveHttpResponse =
        files[fileId]?.let { GoogleDriveHttpResponse(200, it.content) } ?: error(404, "notFound")

    private fun delete(fileId: String): GoogleDriveHttpResponse =
        if (files.remove(fileId) != null) GoogleDriveHttpResponse(204) else error(404, "notFound")

    private fun list(query: String, pageToken: String?): GoogleDriveHttpResponse {
        val matching = files.values.filter { matches(it, query) }
        val start = pageToken?.toInt() ?: 0
        val page = matching.drop(start).take(pageSize)
        val body = JsonObject().apply {
            if (start + pageSize < matching.size) addProperty("nextPageToken", (start + pageSize).toString())
            add(
                "files",
                JsonArray().apply {
                    page.forEach { file ->
                        add(
                            JsonObject().apply {
                                addProperty("id", file.id)
                                addProperty("name", file.name)
                                file.description?.let { addProperty("description", it) }
                                file.modifiedTime?.let { addProperty("modifiedTime", it) }
                            }
                        )
                    }
                }
            )
        }
        return GoogleDriveHttpResponse(200, body.toString().toByteArray(StandardCharsets.UTF_8))
    }

    // Only the three query shapes wallet backup sends.
    private fun matches(file: StoredDriveFile, query: String): Boolean {
        NAME_QUERY.matchEntire(query)?.let { return file.name == it.groupValues[1] }
        ROOT_QUERY.matchEntire(query)?.let { return "appDataFolder" in file.parents && file.mimeType != it.groupValues[2] }
        val parents = query.split(" or ").map { term ->
            PARENT_TERM.matchEntire(term)?.groupValues?.get(1) ?: error("unsupported query: $query")
        }
        return file.parents.any { it in parents }
    }

    private fun createFolder(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        val metadata = JsonParser.parseString(requireNotNull(request.body).toString(StandardCharsets.UTF_8)).asJsonObject
        val id = "folder-${nextId++}"
        add(
            StoredDriveFile(
                id = id,
                name = metadata.get("name").asString,
                mimeType = metadata.get("mimeType").asString,
                parents = metadata.getAsJsonArray("parents").map { it.asString }
            )
        )
        return GoogleDriveHttpResponse(200, "{\"id\":\"$id\"}".toByteArray())
    }

    private fun createMultipart(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
        val parts = MultipartRelated.parse(request)
        val metadata = JsonParser.parseString(parts.metadata).asJsonObject
        val id = "file-${nextId++}"
        add(
            StoredDriveFile(
                id = id,
                name = metadata.get("name").asString,
                mimeType = metadata.get("mimeType").asString,
                parents = metadata.getAsJsonArray("parents").map { it.asString },
                description = metadata.get("description")?.asString,
                content = parts.media,
                modifiedTime = "2026-11-07T00:00:0${nextId % 10}.000Z"
            )
        )
        return GoogleDriveHttpResponse(200, "{\"id\":\"$id\",\"name\":\"x\",\"description\":\"y\"}".toByteArray())
    }

    private companion object {
        val NAME_QUERY = Regex("^name = '([^']*)'$")
        val ROOT_QUERY = Regex("^'(appDataFolder)' in parents and mimeType != '([^']*)'$")
        val PARENT_TERM = Regex("^'([^']*)' in parents$")

        fun error(code: Int, reason: String) = GoogleDriveHttpResponse(
            code,
            "{\"error\":{\"code\":$code,\"message\":\"$reason\",\"errors\":[{\"domain\":\"global\",\"reason\":\"$reason\"}]}}"
                .toByteArray()
        )
    }
}

/** The two parts of a Drive multipart/related upload, split exactly as Drive reads them. */
internal class MultipartRelated(
    val metadataContentType: String,
    val metadata: String,
    val mediaContentType: String,
    val media: ByteArray
) {
    companion object {
        fun parse(request: GoogleDriveHttpRequest): MultipartRelated {
            val contentType = requireNotNull(request.headers["Content-Type"])
            require(contentType.startsWith("multipart/related; boundary="))
            val boundary = contentType.substringAfter("boundary=")
            val body = requireNotNull(request.body).toString(StandardCharsets.ISO_8859_1)
            require(body.startsWith("--$boundary\r\n") && body.endsWith("\r\n--$boundary--\r\n"))
            val parts = body.removePrefix("--$boundary\r\n").removeSuffix("\r\n--$boundary--\r\n").split("\r\n--$boundary\r\n")
            require(parts.size == 2)
            val (metadataHead, metadata) = parts[0].split("\r\n\r\n", limit = 2)
            val (mediaHead, media) = parts[1].split("\r\n\r\n", limit = 2)
            return MultipartRelated(
                metadataContentType = metadataHead.removePrefix("Content-Type: "),
                metadata = metadata.removeSuffix("\r\n").toByteArray(StandardCharsets.ISO_8859_1).toString(StandardCharsets.UTF_8),
                mediaContentType = mediaHead.removePrefix("Content-Type: "),
                media = media.toByteArray(StandardCharsets.ISO_8859_1)
            )
        }
    }
}

/** Hands out [tokens] in order and records rejections. */
internal class FakeTokenSource(
    private val tokens: List<String> = listOf("ya29.synthetic")
) : GoogleDriveAccessTokenSource {
    var issued = 0
    val rejected = mutableListOf<String>()
    var signedIn = true

    override suspend fun accessToken(): String {
        if (!signedIn) throw jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException()
        return tokens[minOf(issued++, tokens.size - 1)]
    }

    override suspend fun onAccessTokenRejected(token: String) {
        rejected += token
    }
}
