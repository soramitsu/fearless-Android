package jp.co.soramitsu.backup.data

import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.drive.Drive
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.FileMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Drive requests and responses on a mock transport: no network, no Google Play services. */
class GoogleDriveFilesRequestTest {

    private val drive = FakeDrive()

    @Test
    fun `looks up the backup folder in the app data space`() {
        drive.respond { request -> if (request.isFolderLookup) folders("folder-1") else filePage() }

        GoogleDriveFiles(drive.client).listAppFiles()

        val lookup = drive.requests.first()
        assertEquals("GET", lookup.method)
        assertEquals("name = 'backupFolder'", lookup.query("q"))
        assertEquals("appDataFolder", lookup.query("spaces"))
        assertEquals("nextPageToken, files(id)", lookup.query("fields"))
    }

    @Test
    fun `lists app backups in the backup folder with their modified time`() {
        drive.respond { request ->
            if (request.isFolderLookup) folders("folder-1") else filePage(file("f1", "5A.json", "Wallet", "2024-06-01T10:00:00.000Z"))
        }

        val files = GoogleDriveFiles(drive.client).listAppFiles()

        val list = drive.requests.last()
        assertEquals("'folder-1' in parents", list.query("q"))
        assertEquals("appDataFolder", list.query("spaces"))
        assertEquals("nextPageToken, files(id, name, description, modifiedTime)", list.query("fields"))
        assertEquals(listOf(FileMeta("5A.json", "Wallet", "f1", 1_717_236_000_000)), files)
    }

    @Test
    fun `lists every folder named backupFolder and never creates one while listing`() {
        drive.respond { request -> if (request.isFolderLookup) folders("folder-1", "folder-2") else filePage() }

        GoogleDriveFiles(drive.client).listAppFiles()
        assertEquals("'folder-1' in parents or 'folder-2' in parents", drive.requests.last().query("q"))

        drive.requests.clear()
        drive.respond { folders() }
        assertEquals(emptyList<FileMeta>(), GoogleDriveFiles(drive.client).listAppFiles())
        assertEquals(listOf("GET"), drive.requests.map { it.method })
    }

    @Test
    fun `lists extension backups in the app data root without folders`() {
        drive.respond { filePage(file("w1", "My wallet.json", "5A/e1", null)) }

        val files = GoogleDriveFiles(drive.client).listWebFiles()

        val list = drive.requests.single()
        assertEquals("'appDataFolder' in parents and mimeType != 'application/vnd.google-apps.folder'", list.query("q"))
        assertEquals("appDataFolder", list.query("spaces"))
        assertEquals(listOf(FileMeta("My wallet.json", "5A/e1", "w1", null)), files)
    }

    @Test
    fun `follows every page`() {
        drive.respond { request ->
            when (request.query("pageToken")) {
                null -> filePage(file("w1", "a.json", "", null), nextPageToken = "page-2")
                "page-2" -> filePage(file("w2", "b.json", "", null))
                else -> error("unexpected page")
            }
        }

        val files = GoogleDriveFiles(drive.client).listWebFiles()

        assertEquals(listOf("w1", "w2"), files.map { it.fileId })
        assertEquals(listOf(null, "page-2"), drive.requests.map { it.query("pageToken") })
    }

    @Test
    fun `creates the backup folder in the app data root when saving the first backup`() {
        drive.respond { request ->
            when {
                request.isFolderLookup -> folders()
                request.method == "POST" && request.url.startsWith(FILES_URL) -> json("""{"id":"folder-new"}""")
                request.query("uploadType") == "resumable" -> MockLowLevelHttpResponse().addHeader("Location", UPLOAD_SESSION_URL)
                request.url == UPLOAD_SESSION_URL -> json("""{"id":"file-new"}""")
                else -> error("unexpected ${request.method} ${request.url}")
            }
        }

        val fileId = GoogleDriveFiles(drive.client).createFile("5A.json", "Wallet", """{"name":"Wallet"}""")

        assertEquals("file-new", fileId)
        val folderCreate = drive.requests.single { it.method == "POST" && it.url.startsWith(FILES_URL) }
        assertEquals("id", folderCreate.query("fields"))
        val folder = Json.parseToJsonElement(folderCreate.body).jsonObject
        assertEquals("application/vnd.google-apps.folder", folder.getValue("mimeType").jsonPrimitive.content)
        assertEquals("backupFolder", folder.getValue("name").jsonPrimitive.content)
        assertEquals(listOf("appDataFolder"), folder.getValue("parents").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `uploads app backups as text json named by address and described by wallet name`() {
        drive.respond { request ->
            when {
                request.isFolderLookup -> folders("folder-1")
                request.query("uploadType") == "resumable" -> MockLowLevelHttpResponse().addHeader("Location", UPLOAD_SESSION_URL)
                request.url == UPLOAD_SESSION_URL -> json("""{"id":"file-new"}""")
                else -> error("unexpected ${request.method} ${request.url}")
            }
        }

        GoogleDriveFiles(drive.client).createFile("5A.json", "Wallet", """{"name":"Wallet"}""")

        val start = drive.requests.single { it.query("uploadType") == "resumable" }
        assertEquals("POST", start.method)
        assertEquals("id, description, name", start.query("fields"))
        val metadata = Json.parseToJsonElement(start.body).jsonObject
        assertEquals("5A.json", metadata.getValue("name").jsonPrimitive.content)
        assertEquals("Wallet", metadata.getValue("description").jsonPrimitive.content)
        assertEquals("text/json", metadata.getValue("mimeType").jsonPrimitive.content)
        assertEquals(listOf("folder-1"), metadata.getValue("parents").jsonArray.map { it.jsonPrimitive.content })
        val upload = drive.requests.single { it.url == UPLOAD_SESSION_URL }
        assertEquals("text/json", upload.contentType)
        assertEquals("""{"name":"Wallet"}""", upload.body)
    }

    @Test
    fun `downloads content with alt media`() {
        drive.respond { MockLowLevelHttpResponse().setContent("backup content") }

        val content = GoogleDriveFiles(drive.client).getFileContent("file-1")

        assertEquals("backup content", content)
        val get = drive.requests.single()
        assertEquals("GET", get.method)
        assertEquals("https://www.googleapis.com/download/drive/v3/files/file-1", get.url.substringBefore("?"))
        assertEquals("media", get.query("alt"))
    }

    @Test
    fun `maps a missing file to FileNotFoundException`() {
        drive.respond { driveError(404, "notFound") }

        assertThrows(FileNotFoundException::class.java) { GoogleDriveFiles(drive.client).deleteFile("gone") }
        assertThrows(FileNotFoundException::class.java) { GoogleDriveFiles(drive.client).getFileContent("gone") }
        assertEquals("DELETE", drive.requests.first().method)
    }

    @Test
    fun `maps a full Drive to StorageQuotaExceededException`() {
        drive.respond { request -> if (request.isFolderLookup) folders("folder-1") else driveError(403, "storageQuotaExceeded") }

        assertThrows(StorageQuotaExceededException::class.java) {
            GoogleDriveFiles(drive.client).createFile("5A.json", "Wallet", "{}")
        }
    }

    @Test
    fun `maps missing folder permission to InsufficientFilePermissionsException`() {
        drive.respond { request -> if (request.isFolderLookup) folders() else driveError(403, "insufficientFilePermissions") }

        assertThrows(InsufficientFilePermissionsException::class.java) {
            GoogleDriveFiles(drive.client).createFile("5A.json", "Wallet", "{}")
        }
    }

    @Test
    fun `keeps other Drive errors as they are`() {
        drive.respond { driveError(403, "rateLimitExceeded") }

        assertThrows(com.google.api.client.googleapis.json.GoogleJsonResponseException::class.java) {
            GoogleDriveFiles(drive.client).listWebFiles()
        }
    }

    @Test
    fun `maps recoverable auth to AuthConsentException carrying the consent intent`() {
        val consentIntent = mock<Intent>()
        val recoverable = mock<UserRecoverableAuthIOException> { on { intent } doReturn consentIntent }
        val files = GoogleDriveFiles(FakeDrive(throwingOnExecute(recoverable)).client)

        val failure = assertThrows(AuthConsentException::class.java) { files.listWebFiles() }

        assertSame(consentIntent, failure.intent)
        assertSame(recoverable, failure.cause)
    }

    @Test
    fun `maps auth failures without a consent screen to UnauthorizedException`() {
        val withoutIntent = mock<UserRecoverableAuthIOException> { on { intent } doReturn null }
        val unrecoverable = GoogleAuthIOException(GoogleAuthException("bad authentication"))

        listOf(withoutIntent, unrecoverable).forEach { authFailure ->
            val files = GoogleDriveFiles(FakeDrive(throwingOnExecute(authFailure)).client)
            assertThrows(UnauthorizedException::class.java) { files.listWebFiles() }
        }
    }

    @Test
    fun `requests an ID token only when the build has a web client id`() {
        val unconfigured = mock<GoogleSignInOptions.Builder>(defaultAnswer = Answers.RETURNS_SELF)
        unconfigured.requestIdTokenWhenConfigured("  ")
        verify(unconfigured, never()).requestIdToken(any())

        val configured = mock<GoogleSignInOptions.Builder>(defaultAnswer = Answers.RETURNS_SELF)
        configured.requestIdTokenWhenConfigured("web-client-id")
        verify(configured).requestIdToken("web-client-id")
    }

    private class RecordedRequest(val method: String, val url: String, val contentType: String?, val body: String) {
        private val parsed = GenericUrl(url)

        val isFolderLookup get() = method == "GET" && query("q") == "name = 'backupFolder'"

        fun query(name: String): String? = parsed.getFirst(name) as? String
    }

    private class FakeDrive(initializer: HttpRequestInitializer? = null) {
        val requests = mutableListOf<RecordedRequest>()
        private var responder: (RecordedRequest) -> MockLowLevelHttpResponse = { error("no responder") }

        private val transport = object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
                override fun execute(): MockLowLevelHttpResponse {
                    val recorded = RecordedRequest(method, url, contentType, contentAsString)
                    requests += recorded
                    return responder(recorded)
                }
            }
        }

        val client: Drive = Drive.Builder(transport, GsonFactory.getDefaultInstance(), initializer).build()

        fun respond(responder: (RecordedRequest) -> MockLowLevelHttpResponse) {
            this.responder = responder
        }
    }

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_SESSION_URL = "https://www.googleapis.com/upload/drive/v3/files?upload_id=session"

        // GoogleAccountCredential throws its auth failures while the request executes.
        fun throwingOnExecute(failure: Exception) = HttpRequestInitializer { request ->
            request.setInterceptor { throw failure }
        }

        fun json(body: String): MockLowLevelHttpResponse =
            MockLowLevelHttpResponse().setContentType("application/json").setContent(body)

        fun folders(vararg ids: String) = json("""{"files":[${ids.joinToString(",") { """{"id":"$it"}""" }}]}""")

        fun file(
            id: String,
            name: String,
            description: String,
            modifiedTime: String?
        ): String =
            """{"id":"$id","name":"$name","description":"$description"${modifiedTime?.let { ""","modifiedTime":"$it"""" }.orEmpty()}}"""

        fun filePage(vararg files: String, nextPageToken: String? = null) =
            json("""{${nextPageToken?.let { """"nextPageToken":"$it",""" }.orEmpty()}"files":[${files.joinToString(",")}]}""")

        fun driveError(code: Int, reason: String): MockLowLevelHttpResponse = json(
            """{"error":{"code":$code,"message":"$reason","errors":[{"domain":"global","reason":"$reason","message":"$reason"}]}}"""
        ).setStatusCode(code)
    }
}
