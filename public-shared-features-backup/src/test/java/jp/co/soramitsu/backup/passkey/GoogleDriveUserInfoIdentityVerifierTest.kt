package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class GoogleDriveUserInfoIdentityVerifierTest {
    @Test
    fun `same bearer reaches only fixed Google userinfo endpoint with bounded response`() = runBlocking {
        val transport = RecordingIdentityTransport(valid())
        val identity = GoogleDriveUserInfoIdentityVerifier(transport).verify(" token-123 ")
        assertEquals("stable-google-subject", identity.subject)
        assertEquals("user@example.com", identity.email)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://openidconnect.googleapis.com/v1/userinfo", request.url)
        assertEquals("Bearer token-123", request.headers["Authorization"])
        assertEquals("no-store", request.headers["Cache-Control"])
        assertEquals(8192, request.maxResponseBytes)
        assertEquals(null, request.body)
        assertFalse(identity.toString().contains("user@example.com"))
        assertFalse(request.toString().contains("token-123"))
    }

    @Test
    fun `verified Google subject is not derived from email`() = runBlocking {
        val verifier = GoogleDriveUserInfoIdentityVerifier(RecordingIdentityTransport(valid(email = "renamed@example.com")))
        val identity = verifier.verify("token-123")
        assertEquals("stable-google-subject", identity.subject)
        assertEquals("renamed@example.com", identity.email)
    }

    @Test
    fun `missing false or wrong typed verification and claims fail closed`() = runBlocking {
        val responses = listOf(
            "{}", "null", "[]", "not-json",
            """{"sub":"subject","email":"user@example.com"}""",
            """{"sub":"subject","email":"user@example.com","email_verified":false}""",
            """{"sub":"subject","email":"user@example.com","email_verified":"true"}""",
            """{"sub":123,"email":"user@example.com","email_verified":true}""",
            """{"sub":"subject","email":null,"email_verified":true}""",
            """{"sub":"subject","email":"invalid","email_verified":true}""",
            """{"sub":"subject space","email":"user@example.com","email_verified":true}"""
        )
        for (json in responses) {
            val failure = runCatching { GoogleDriveUserInfoIdentityVerifier(RecordingIdentityTransport(json)).verify("token-123") }
            assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `duplicate claims trailing JSON and non scalar extensions are rejected`() = runBlocking {
        for (json in listOf(
            valid().replace("\"sub\":", "\"sub\":\"other\",\"sub\":"),
            valid().replace("\"email_verified\":", "\"email_verified\":false,\"email_verified\":"),
            valid() + " {}",
            valid().dropLast(1) + ",\"nested\":{}}",
            valid().dropLast(1) + ",\"extension\":[],\"other\":null}",
            valid().dropLast(1) + ",\"hd\":\"a\",\"hd\":\"b\"}"
        )) {
            assertTrue(
                runCatching {
                    GoogleDriveUserInfoIdentityVerifier(RecordingIdentityTransport(json)).verify("token-123")
                }.isFailure
            )
        }
    }

    @Test
    fun `optional scalar fields are tolerated without identity substitution`() = runBlocking {
        val json = valid().dropLast(1) + ",\"hd\":\"example.com\",\"optional\":null,\"count\":1}"
        val identity = GoogleDriveUserInfoIdentityVerifier(RecordingIdentityTransport(json)).verify("token-123")
        assertEquals("stable-google-subject", identity.subject)
    }

    @Test
    fun `oversized invalid utf8 or too many identity fields are rejected`() = runBlocking {
        val tooMany = valid().dropLast(1) + (1..33).joinToString("") { ",\"extra$it\":true" } + "}"
        for (bytes in listOf(ByteArray(8193), byteArrayOf(0xC3.toByte(), 0x28), tooMany.toByteArray())) {
            val verifier = GoogleDriveUserInfoIdentityVerifier(object : GoogleDriveHttpTransport {
                override suspend fun execute(request: GoogleDriveHttpRequest) = GoogleDriveHttpResponse(200, bytes)
            })
            assertTrue(runCatching { verifier.verify("token-123") }.isFailure)
        }
    }

    @Test
    fun `invalid bearer cannot reach Google and subject validation has no normalization fallback`() = runBlocking {
        val verifier = GoogleDriveUserInfoIdentityVerifier(object : GoogleDriveHttpTransport {
            override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse =
                error("Network forbidden")
        })
        for (token in listOf("", "\n", "token\r\nInjected:true")) {
            assertTrue(runCatching { verifier.verify(token) }.exceptionOrNull() is IllegalArgumentException)
        }
        for (subject in listOf("", " subject", "subject ", "google:email", "a".repeat(256))) {
            assertTrue(runCatching { GoogleDriveVerifiedIdentity(subject, "user@example.com", true) }.isFailure)
        }
        assertTrue(runCatching { GoogleDriveVerifiedIdentity("subject", "user@example.com", false) }.isFailure)
    }

    @Test
    fun `redirect and server errors never count as verified identity`() = runBlocking {
        for (status in listOf(301, 302, 307, 308, 401, 403, 500)) {
            val transport = RecordingIdentityTransport(valid(), status)
            assertTrue(runCatching { GoogleDriveUserInfoIdentityVerifier(transport).verify("token-123") }.isFailure)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun `real OkHttp transport does not forward UserInfo bearer across redirect`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val requests = AtomicInteger()
            val worker = thread {
                try {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume the synthetic request. */ }
                        requests.incrementAndGet()
                        val response = "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${server.localPort}/other\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().write(response.toByteArray())
                    }
                    server.soTimeout = 1000
                    server.accept().use { requests.incrementAndGet() }
                } catch (_: SocketTimeoutException) {
                    // No follow-up is the expected result.
                }
            }
            val http = OkHttpGoogleDriveHttpTransport(OkHttpClient.Builder().followRedirects(true).build())
            val localOnly = object : GoogleDriveHttpTransport {
                override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                    assertEquals(GoogleDriveUserInfoIdentityVerifier.USER_INFO_URL, request.url)
                    return http.execute(request.copy(url = "http://127.0.0.1:${server.localPort}/userinfo"))
                }
            }
            val failure = withTimeout(5000) {
                runCatching { GoogleDriveUserInfoIdentityVerifier(localOnly).verify("synthetic-token") }.exceptionOrNull()
            }
            worker.join(2000)
            assertTrue(failure is IllegalArgumentException)
            assertEquals(1, requests.get())
            assertFalse(worker.isAlive)
        }
    }

    private fun valid(email: String = "user@example.com") =
        """{"sub":"stable-google-subject","email":"$email","email_verified":true}"""

    private class RecordingIdentityTransport(private val json: String, private val status: Int = 200) : GoogleDriveHttpTransport {
        val requests = mutableListOf<GoogleDriveHttpRequest>()
        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            return GoogleDriveHttpResponse(status, json.toByteArray())
        }
    }
}
