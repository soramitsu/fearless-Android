package jp.co.soramitsu.backup.data

import android.content.Intent
import com.google.gson.JsonParser
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.FileMeta
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/** Drive requests and responses on a fake transport: no network, no Google SDK. */
class GoogleDriveFilesRequestTest {

    private val drive = FakeDrive()
    private val tokens = FakeTokenSource()
    private val consent = mock<Intent>()
    private var consentAvailable = true
    private val files = GoogleDriveFiles(GoogleDriveApi(drive, tokens)) { consent.takeIf { consentAvailable } }

    private fun GoogleDriveHttpRequest.query(name: String): String? = url.toHttpUrl().queryParameter(name)

    private fun respond(responder: (GoogleDriveHttpRequest) -> GoogleDriveHttpResponse?) {
        drive.override = responder
    }

    @Test
    fun `looks up the backup folder in the app data space`() = runBlocking {
        files.listAppFiles()

        val lookup = drive.requests.first()
        assertEquals("GET", lookup.method)
        assertEquals("https://www.googleapis.com/drive/v3/files", lookup.url.substringBefore('?'))
        assertEquals("name = 'backupFolder'", lookup.query("q"))
        assertEquals("appDataFolder", lookup.query("spaces"))
        assertEquals("nextPageToken, files(id)", lookup.query("fields"))
        assertEquals("Bearer ya29.synthetic", lookup.headers["Authorization"])
    }

    @Test
    fun `lists app backups in the backup folder with their modified time`() = runBlocking {
        drive.add(StoredDriveFile("folder-1", "backupFolder", FOLDER, listOf("appDataFolder")))
        drive.add(StoredDriveFile("f1", "5A.json", "text/json", listOf("folder-1"), "Wallet", modifiedTime = "2024-06-01T10:00:00.000Z"))

        val listed = files.listAppFiles()

        val list = drive.requests.last()
        assertEquals("'folder-1' in parents", list.query("q"))
        assertEquals("appDataFolder", list.query("spaces"))
        assertEquals("nextPageToken, files(id, name, description, modifiedTime)", list.query("fields"))
        assertEquals(listOf(FileMeta("5A.json", "Wallet", "f1", 1_717_236_000_000)), listed)
    }

    @Test
    fun `lists every folder named backupFolder and never creates one while listing`() = runBlocking {
        drive.add(StoredDriveFile("folder-1", "backupFolder", FOLDER, listOf("appDataFolder")))
        drive.add(StoredDriveFile("folder-2", "backupFolder", FOLDER, listOf("appDataFolder")))

        files.listAppFiles()
        assertEquals("'folder-1' in parents or 'folder-2' in parents", drive.requests.last().query("q"))

        drive.files.clear()
        drive.requests.clear()
        assertEquals(emptyList<FileMeta>(), files.listAppFiles())
        assertEquals(listOf("GET"), drive.requests.map { it.method })
    }

    @Test
    fun `lists extension backups in the app data root without folders`() = runBlocking {
        drive.add(StoredDriveFile("folder-1", "backupFolder", FOLDER, listOf("appDataFolder")))
        drive.add(StoredDriveFile("w1", "My wallet.json", "application/json", listOf("appDataFolder"), "5A/e1"))

        val listed = files.listWebFiles()

        val list = drive.requests.single()
        assertEquals("'appDataFolder' in parents and mimeType != 'application/vnd.google-apps.folder'", list.query("q"))
        assertEquals("appDataFolder", list.query("spaces"))
        assertEquals(listOf(FileMeta("My wallet.json", "5A/e1", "w1", null)), listed)
    }

    @Test
    fun `follows every page`() = runBlocking {
        val paged = FakeDrive(pageSize = 1)
        paged.add(StoredDriveFile("w1", "a.json", "text/json", listOf("appDataFolder"), ""))
        paged.add(StoredDriveFile("w2", "b.json", "text/json", listOf("appDataFolder"), ""))
        val pagedFiles = GoogleDriveFiles(GoogleDriveApi(paged, tokens)) { null }

        val listed = pagedFiles.listWebFiles()

        assertEquals(listOf("w1", "w2"), listed.map { it.fileId })
        assertEquals(listOf(null, "1"), paged.requests.map { it.query("pageToken") })
    }

    @Test
    fun `a page token Drive already returned fails instead of looping`() = runBlocking {
        respond { GoogleDriveHttpResponse(200, """{"nextPageToken":"again","files":[]}""".toByteArray()) }

        val failure = runCatching { files.listWebFiles() }.exceptionOrNull()

        assertTrue(failure is GoogleDriveApi.MalformedDriveResponseException)
        assertEquals(2, drive.requests.size)
    }

    @Test
    fun `creates the backup folder in the app data root when saving the first backup`() = runBlocking {
        val fileId = files.createFile("5A.json", "Wallet", """{"name":"Wallet"}""")

        val folderCreate = drive.requests.single { request ->
            request.method == "POST" && request.url.startsWith("https://www.googleapis.com/drive/v3/files")
        }
        assertEquals("id", folderCreate.query("fields"))
        assertEquals("application/json; charset=UTF-8", folderCreate.headers["Content-Type"])
        val folder = JsonParser.parseString(requireNotNull(folderCreate.body).decodeToString()).asJsonObject
        assertEquals(setOf("parents", "mimeType", "name"), folder.keySet())
        assertEquals("application/vnd.google-apps.folder", folder.get("mimeType").asString)
        assertEquals("backupFolder", folder.get("name").asString)
        assertEquals(listOf("appDataFolder"), folder.getAsJsonArray("parents").map { it.asString })
        assertEquals(listOf("folder-1"), drive.files.getValue(fileId).parents)
    }

    @Test
    fun `uploads app backups as text json named by address and described by wallet name`() = runBlocking {
        drive.add(StoredDriveFile("folder-1", "backupFolder", FOLDER, listOf("appDataFolder")))

        files.createFile("5A.json", "Wallet \"é\" <&>", """{"name":"Wallet"}""")

        val upload = drive.requests.single { it.method == "POST" }
        assertEquals("https://www.googleapis.com/upload/drive/v3/files", upload.url.substringBefore('?'))
        assertEquals("multipart", upload.query("uploadType"))
        assertEquals("id, description, name", upload.query("fields"))
        val parts = MultipartRelated.parse(upload)
        assertEquals("application/json; charset=UTF-8", parts.metadataContentType)
        val metadata = JsonParser.parseString(parts.metadata).asJsonObject
        assertEquals(setOf("parents", "mimeType", "name", "description"), metadata.keySet())
        assertEquals("5A.json", metadata.get("name").asString)
        assertEquals("Wallet \"é\" <&>", metadata.get("description").asString)
        assertEquals("text/json", metadata.get("mimeType").asString)
        assertEquals(listOf("folder-1"), metadata.getAsJsonArray("parents").map { it.asString })
        assertEquals("text/json", parts.mediaContentType)
        assertEquals("""{"name":"Wallet"}""", parts.media.decodeToString())
    }

    @Test
    fun `downloads content with alt media`() = runBlocking {
        drive.add(StoredDriveFile("file-1", "5A.json", "text/json", listOf("folder-1"), content = "backup content".toByteArray()))

        val content = files.getFileContent("file-1")

        assertEquals("backup content", content)
        val get = drive.requests.single()
        assertEquals("GET", get.method)
        assertEquals("https://www.googleapis.com/drive/v3/files/file-1", get.url.substringBefore("?"))
        assertEquals("media", get.query("alt"))
    }

    @Test
    fun `maps a missing file to FileNotFoundException`() = runBlocking {
        assertTrue(runCatching { files.deleteFile("gone") }.exceptionOrNull() is FileNotFoundException)
        assertTrue(runCatching { files.getFileContent("gone") }.exceptionOrNull() is FileNotFoundException)
        assertEquals("DELETE", drive.requests.first().method)
        assertEquals("https://www.googleapis.com/drive/v3/files/gone", drive.requests.first().url)
    }

    @Test
    fun `deletes by file id`() = runBlocking {
        drive.add(StoredDriveFile("file-1", "5A.json", "text/json", listOf("folder-1")))

        files.deleteFile("file-1")

        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun `a file id that could change the request path is refused before any request`() = runBlocking {
        listOf("../files", "a/b", "a?alt=media", "", "a b").forEach { fileId ->
            assertTrue(fileId, runCatching { files.getFileContent(fileId) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun `maps a full Drive to StorageQuotaExceededException`() = runBlocking {
        drive.add(StoredDriveFile("folder-1", "backupFolder", FOLDER, listOf("appDataFolder")))
        respond { request -> if (request.method == "POST") driveError(403, "storageQuotaExceeded") else null }

        val failure = runCatching { files.createFile("5A.json", "Wallet", "{}") }.exceptionOrNull()
        assertTrue(failure is StorageQuotaExceededException)
    }

    @Test
    fun `maps missing folder permission to InsufficientFilePermissionsException`() = runBlocking {
        respond { request -> if (request.method == "POST") driveError(403, "insufficientFilePermissions") else null }

        assertTrue(
            runCatching { files.createFile("5A.json", "Wallet", "{}") }.exceptionOrNull() is InsufficientFilePermissionsException
        )
    }

    @Test
    fun `keeps other Drive errors as retryable I-O failures`() = runBlocking {
        listOf(driveError(403, "rateLimitExceeded"), driveError(429, "userRateLimitExceeded"), driveError(500, "backendError"))
            .forEach { response ->
                respond { response }
                val failure = runCatching { files.listWebFiles() }.exceptionOrNull()
                assertTrue(failure is GoogleDriveHttpException && failure.statusCode == response.code)
            }
    }

    @Test
    fun `Drive access turned off asks for consent again, or for sign-in without a consent screen`() = runBlocking {
        listOf(
            driveError(403, "insufficientPermissions"),
            GoogleDriveHttpResponse(
                403,
                """{"error":{"code":403,"status":"PERMISSION_DENIED","details":[{"reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}"""
                    .toByteArray()
            )
        ).forEach { response ->
            respond { response }
            consentAvailable = true
            val failure = runCatching { files.listWebFiles() }.exceptionOrNull()
            assertTrue(failure is AuthConsentException)
            assertSame(consent, (failure as AuthConsentException).intent)
            assertTrue(failure.cause is GoogleDriveHttpException)

            consentAvailable = false
            assertTrue(runCatching { files.listWebFiles() }.exceptionOrNull() is UnauthorizedException)
        }
    }

    @Test
    fun `an expired bearer token is dropped and retried once with a fresh token`() = runBlocking {
        val refreshing = FakeTokenSource(listOf("ya29.expired", "ya29.synthetic"))
        val retrying = GoogleDriveFiles(GoogleDriveApi(drive, refreshing)) { null }

        assertEquals(emptyList<FileMeta>(), retrying.listWebFiles())

        assertEquals(listOf("Bearer ya29.expired", "Bearer ya29.synthetic"), drive.requests.map { it.headers["Authorization"] })
        assertEquals(listOf("ya29.expired"), refreshing.rejected)
    }

    @Test
    fun `a repeated unauthorized response means signing in again`() = runBlocking {
        val stale = FakeTokenSource(listOf("ya29.expired", "ya29.still-expired"))
        val rejected = GoogleDriveFiles(GoogleDriveApi(drive, stale)) { consent }

        val failure = runCatching { rejected.listWebFiles() }.exceptionOrNull()

        assertTrue(failure is UnauthorizedException)
        assertEquals(2, drive.requests.size)
        assertEquals(listOf("ya29.expired", "ya29.still-expired"), stale.rejected)
    }

    @Test
    fun `nobody signed in blocks every Drive request`() = runBlocking {
        tokens.signedIn = false

        assertTrue(runCatching { files.createFile("5A.json", "Wallet", "{}") }.exceptionOrNull() is UnauthorizedException)
        assertTrue(runCatching { files.listAppFiles() }.exceptionOrNull() is UnauthorizedException)
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun `malformed listings are refused`() = runBlocking {
        listOf(
            """{"files":{}}""",
            """{"files":[{"name":"no id"}]}""",
            """{"files":[{"id":"../x","name":"a"}]}""",
            """{"files":[],"nextPageToken":7}""",
            "not json"
        ).forEach { body ->
            respond { GoogleDriveHttpResponse(200, body.toByteArray()) }
            val failure = runCatching { files.listWebFiles() }.exceptionOrNull()
            assertTrue(body, failure is GoogleDriveApi.MalformedDriveResponseException)
        }
        respond { GoogleDriveHttpResponse(200, """{"files":[{"id":"w1"}]}""".toByteArray()) }
        assertTrue(runCatching { files.listWebFiles() }.exceptionOrNull() is GoogleDriveApi.MalformedDriveResponseException)
    }

    @Test
    fun `an unreadable modified time only loses the ordering hint`() {
        assertEquals(1_717_236_000_000, GoogleDriveApi.parseRfc3339Millis("2024-06-01T10:00:00.000Z"))
        assertEquals(1_717_236_000_000, GoogleDriveApi.parseRfc3339Millis("2024-06-01T19:00:00+09:00"))
        assertEquals(null, GoogleDriveApi.parseRfc3339Millis("yesterday"))
    }

    private companion object {
        const val FOLDER = "application/vnd.google-apps.folder"

        fun driveError(code: Int, reason: String) = GoogleDriveHttpResponse(
            code,
            """{"error":{"code":$code,"message":"$reason","errors":[{"domain":"global","reason":"$reason","message":"$reason"}]}}"""
                .toByteArray()
        )
    }
}
