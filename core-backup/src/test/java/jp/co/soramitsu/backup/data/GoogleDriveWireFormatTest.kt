package jp.co.soramitsu.backup.data

import jp.co.soramitsu.backup.oauth.GoogleOAuthAuthorizationRequest
import jp.co.soramitsu.backup.oauth.GoogleOAuthConfig
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenClient
import jp.co.soramitsu.backup.passkey.OkHttpGoogleDriveHttpTransport
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom

/**
 * The bytes OkHttp actually sends, captured by an application interceptor that answers in place of
 * Google (MockWebServer is not part of this build). Nothing leaves the machine.
 */
class GoogleDriveWireFormatTest {

    private class CapturedRequest(val request: Request, val body: ByteArray)

    private val captured = mutableListOf<CapturedRequest>()
    private var answer: (Request) -> Pair<Int, String> = { 200 to "{}" }

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val body = Buffer().also { request.body?.writeTo(it) }.readByteArray()
                captured += CapturedRequest(request, body)
                val (code, text) = answer(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("synthetic")
                    .body(text.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        )
        .build()
    private val transport = OkHttpGoogleDriveHttpTransport(client)

    @Test
    fun `listing query, fields and bearer are encoded on the wire`() = runBlocking {
        answer = { 200 to """{"files":[{"id":"w1","name":"My wallet.json","description":"5A"}]}""" }
        val files = GoogleDriveFiles(GoogleDriveApi(transport, FakeTokenSource())) { null }

        files.listWebFiles()

        val request = captured.single().request
        assertEquals("GET", request.method)
        assertEquals("Bearer ya29.synthetic", request.header("Authorization"))
        assertEquals(
            "'appDataFolder' in parents and mimeType != 'application/vnd.google-apps.folder'",
            request.url.queryParameter("q")
        )
        assertTrue(request.url.encodedQuery.orEmpty().contains("q=%27appDataFolder%27%20in%20parents"))
        assertEquals("nextPageToken, files(id, name, description, modifiedTime)", request.url.queryParameter("fields"))
    }

    @Test
    fun `multipart upload carries the metadata and the text json media in one body`() = runBlocking {
        answer = { request ->
            if (request.url.queryParameter("q") != null) 200 to """{"files":[{"id":"folder-1"}]}""" else 200 to """{"id":"file-9"}"""
        }
        val files = GoogleDriveFiles(GoogleDriveApi(transport, FakeTokenSource(), SecureRandom())) { null }

        assertEquals("file-9", files.createFile("5A.json", "Wallet", """{"address":"5A"}"""))

        val upload = captured.last()
        assertEquals("POST", upload.request.method)
        assertEquals("multipart", upload.request.url.queryParameter("uploadType"))
        val contentType = requireNotNull(upload.request.body?.contentType()).toString()
        assertTrue(contentType, contentType.startsWith("multipart/related; boundary=fearless-backup-"))
        val boundary = contentType.substringAfter("boundary=")
        val text = upload.body.decodeToString()
        assertTrue(text.startsWith("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{"))
        assertTrue(text.contains("\r\n--$boundary\r\nContent-Type: text/json\r\n\r\n{\"address\":\"5A\"}\r\n--$boundary--\r\n"))
        assertTrue(upload.request.body?.isOneShot() == true)
    }

    @Test
    fun `delete is a bodiless DELETE of the file path`() = runBlocking {
        answer = { 204 to "" }
        val files = GoogleDriveFiles(GoogleDriveApi(transport, FakeTokenSource())) { null }

        files.deleteFile("file-1")

        val request = captured.single().request
        assertEquals("DELETE", request.method)
        assertEquals("https://www.googleapis.com/drive/v3/files/file-1", request.url.toString())
    }

    @Test
    fun `token exchange is a form post without a secret and a non-200 answer fails`() = runBlocking {
        answer = { 302 to "" }
        val config = GoogleOAuthConfig.create("")
        val tokenClient = GoogleOAuthTokenClient(config, transport)
        val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())

        val failure = runCatching { tokenClient.exchangeCode("4/c", request) }.exceptionOrNull()

        assertTrue(failure is IOException)
        val sent = captured.single()
        assertEquals("https://oauth2.googleapis.com/token", sent.request.url.toString())
        assertEquals("application/x-www-form-urlencoded", sent.request.body?.contentType().toString())
        val form = sent.body.decodeToString()
        assertTrue(form.contains("code_verifier=${request.codeVerifier}"))
        assertTrue(form.contains("grant_type=authorization_code"))
        assertTrue(!form.contains("client_secret"))
        assertNull(sent.request.header("Authorization"))
    }
}
