package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class GoogleDrivePasskeyBackupCloudStorageTest {
    @Test
    fun `save creates appDataFolder multipart upload when backup does not exist`() = runBlocking {
        val transport = RecordingDriveTransport(
            jsonResponse("""{"files":[]}"""),
            jsonResponse("""{"id":"drive-file"}""")
        )
        val storage = storage(transport)

        val envelope = validTestEnvelope(plaintext = "ABC".toByteArray())
        storage.savePasskeyBackup(payload(encryptedPayload = envelope))

        assertEquals("GET", transport.requests[0].method)
        assertTrue(transport.requests[0].url.startsWith("https://www.googleapis.com/drive/v3/files?"))
        assertTrue(transport.requests[0].url.contains("spaces=appDataFolder"))
        assertTrue(transport.requests[0].url.contains("fields=files%28id%2Cname%2CappProperties%29"))
        assertTrue(transport.requests[0].url.contains("q=name%20%3D%20%27fearless-passkey-backup-wallet-1234.bin%27%20and%20trashed%20%3D%20false"))
        assertEquals("Bearer token-123", transport.requests[0].headers["Authorization"])

        val upload = transport.requests[1]
        assertEquals("POST", upload.method)
        assertTrue(upload.url.startsWith("https://www.googleapis.com/upload/drive/v3/files?"))
        assertTrue(upload.url.contains("uploadType=multipart"))
        assertTrue(upload.headers.getValue("Content-Type").startsWith("multipart/related; boundary=fearless-passkey-backup-wallet-1234"))

        val uploadBody = upload.bodyText()
        assertTrue(uploadBody.contains(""""parents":["appDataFolder"]"""))
        assertTrue(uploadBody.contains(""""name":"fearless-passkey-backup-wallet-1234.bin""""))
        assertTrue(uploadBody.contains(""""storageKey":"wallet-1234""""))
        assertTrue(uploadBody.contains(""""walletId":"wallet-001""""))
        assertTrue(uploadBody.contains(""""accountName":"alice@example.com""""))
        assertTrue(uploadBody.contains(""""createdAtMillis":"1767225600000""""))
        assertTrue(uploadBody.contains(""""schemaVersion":"1""""))
        assertTrue(uploadBody.contains("FPBKAEAD"))
    }

    @Test
    fun `save updates existing backup file instead of creating duplicate`() = runBlocking {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson()),
            jsonResponse("""{"id":"file-1"}""")
        )
        val storage = storage(transport)

        storage.savePasskeyBackup(payload())

        assertEquals("PATCH", transport.requests[1].method)
        assertTrue(transport.requests[1].url.startsWith("https://www.googleapis.com/upload/drive/v3/files/file-1?"))
    }

    @Test
    fun `load returns encrypted payload from appDataFolder`() = runBlocking {
        val envelope = validTestEnvelope(plaintext = byteArrayOf(7, 8, 9))
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson()),
            GoogleDriveHttpResponse(code = 200, body = envelope)
        )
        val storage = storage(transport)

        val loaded = storage.loadPasskeyBackup(" wallet-1234 ")

        assertEquals("wallet-1234", loaded?.storageKey)
        assertEquals("wallet-001", loaded?.walletId)
        assertEquals("alice@example.com", loaded?.accountName)
        assertEquals(1_767_225_600_000L, loaded?.createdAtMillis)
        assertArrayEquals(envelope, loaded?.encryptedPayload)
        assertEquals("GET", transport.requests[1].method)
        assertEquals("https://www.googleapis.com/drive/v3/files/file-1?alt=media", transport.requests[1].url)
    }

    @Test
    fun `load returns null when appDataFolder backup is absent`() = runBlocking {
        val transport = RecordingDriveTransport(jsonResponse("""{"files":[]}"""))
        val storage = storage(transport)

        assertNull(storage.loadPasskeyBackup("wallet-1234"))
        assertEquals(1, transport.requests.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects unsupported schema before downloading file`() {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson(schemaVersion = "2"))
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects duplicate Drive backup files`() {
        val transport = RecordingDriveTransport(
            jsonResponse(
                """
                {
                  "files": [
                    {
                      "id":"file-1",
                      "appProperties":{
                        "storageKey":"wallet-1234",
                        "walletId":"wallet-001",
                        "accountName":"alice@example.com",
                        "createdAtMillis":"1767225600000",
                        "schemaVersion":"1"
                      }
                    },
                    {
                      "id":"file-2",
                      "appProperties":{
                        "storageKey":"wallet-1234",
                        "walletId":"wallet-001",
                        "accountName":"alice@example.com",
                        "createdAtMillis":"1767225600000",
                        "schemaVersion":"1"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `load rejects malformed Drive file list`() {
        val transport = RecordingDriveTransport(jsonResponse("""{"unexpected":[]}"""))
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `load rejects missing Drive backup metadata before downloading file`() {
        val transport = RecordingDriveTransport(jsonResponse("""{"files":[{"id":"file-1"}]}"""))
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects mismatched Drive backup storage key metadata before downloading file`() {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson(storageKey = "wallet-5678"))
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects invalid Drive backup account metadata before downloading file`() {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson(accountName = "alice example.com"))
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects invalid Drive backup creation timestamp before downloading file`() {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson(createdAtMillis = "0"))
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `load rejects empty downloaded encrypted payload`() {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson()),
            GoogleDriveHttpResponse(code = 200, body = ByteArray(0))
        )
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test
    fun `delete is idempotent when backup is absent`() = runBlocking {
        val transport = RecordingDriveTransport(jsonResponse("""{"files":[]}"""))
        val storage = storage(transport)

        storage.deletePasskeyBackup("wallet-1234")

        assertEquals(1, transport.requests.size)
        assertEquals("GET", transport.requests.single().method)
    }

    @Test
    fun `delete removes existing backup file`() = runBlocking {
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson()),
            GoogleDriveHttpResponse(code = 204)
        )
        val storage = storage(transport)

        storage.deletePasskeyBackup("wallet-1234")

        assertEquals("DELETE", transport.requests[1].method)
        assertEquals("https://www.googleapis.com/drive/v3/files/file-1", transport.requests[1].url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid storage key is rejected before token or network access`() {
        val transport = RecordingDriveTransport()
        val storage = GoogleDrivePasskeyBackupCloudStorage(
            GoogleDrivePasskeyBackupDriveClient(
                accessTokenProvider = GoogleDriveAccessTokenProvider {
                    throw AssertionError("token provider should not be called for invalid keys")
                },
                transport = transport
            )
        )

        runBlocking {
            storage.loadPasskeyBackup("../wallet")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unauthorized Drive response fails closed`() {
        val transport = RecordingDriveTransport(GoogleDriveHttpResponse(code = 401, body = ByteArray(0)))
        val storage = storage(transport)

        runBlocking {
            storage.loadPasskeyBackup("wallet-1234")
        }
    }

    @Test
    fun `OkHttp transport preserves one-shot request body and closes response`() = runBlocking {
        val expectedBody = "drive-response".toByteArray()
        val responseBody = CloseTrackingResponseBody(expectedBody)
        val observedOneShotBody = AtomicBoolean(false)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                observedOneShotBody.set(chain.request().body?.isOneShot() == true)
                response(chain.request(), responseBody)
            }
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)

        val response = transport.execute(
            GoogleDriveHttpRequest(
                method = "POST",
                url = "https://example.invalid/upload",
                headers = mapOf("Content-Type" to GoogleDrivePasskeyBackup.MIME_TYPE),
                body = byteArrayOf(1, 2, 3),
                isOneShot = true
            )
        )

        assertTrue(observedOneShotBody.get())
        assertArrayEquals(expectedBody, response.body)
        assertTrue(responseBody.awaitClosed())
    }

    @Test
    fun `OkHttp transport closes response when reading body fails`() = runBlocking {
        val responseBody = CloseTrackingResponseBody(
            bytes = ByteArray(0),
            readFailure = IOException("simulated response read failure")
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> response(chain.request(), responseBody) }
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)

        val failure = runCatching {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "GET",
                    url = "https://example.invalid/read-failure"
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(responseBody.awaitClosed())
    }

    @Test
    fun `OkHttp transport rejects oversized declared response before reading and closes it`() = runBlocking {
        val readStarted = CountDownLatch(1)
        val responseBody = CloseTrackingResponseBody(
            bytes = ByteArray(0),
            declaredLength = GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES.toLong() + 1,
            readStarted = readStarted
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> response(chain.request(), responseBody) }
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)

        val failure = runCatching {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "GET",
                    url = "https://example.invalid/declared-overflow"
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1L, readStarted.count)
        assertTrue(responseBody.awaitClosed())
    }

    @Test
    fun `OkHttp transport rejects chunked response after bounded overflow and closes it`() = runBlocking {
        val responseBody = CloseTrackingResponseBody(
            bytes = ByteArray(GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES + 1),
            declaredLength = -1
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> response(chain.request(), responseBody) }
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)

        val failure = runCatching {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "GET",
                    url = "https://example.invalid/chunked-overflow"
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(responseBody.awaitClosed())
    }

    @Test
    fun `HTTP request rejects response limits outside bounded contract`() {
        listOf(0, GoogleDrivePasskeyBackup.MAX_HTTP_RESPONSE_BYTES + 1).forEach { limit ->
            assertTrue(
                runCatching {
                    GoogleDriveHttpRequest(
                        method = "GET",
                        url = "https://example.invalid/invalid-limit",
                        maxResponseBytes = limit
                    )
                }.isFailure
            )
        }
    }

    @Test
    fun `cancelling stalled socket request disconnects peer promptly and drains dispatcher`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val acceptedSocket = AtomicReference<Socket?>()
        val requestReceived = CompletableDeferred<Unit>()
        val peerDisconnected = CompletableDeferred<Unit>()
        val callCancelled = CountDownLatch(1)
        val serverJob = async(Dispatchers.IO) {
            server.accept().use { socket ->
                acceptedSocket.set(socket)
                socket.soTimeout = TEST_TIMEOUT_MILLIS.toInt()
                val reader = socket.getInputStream().bufferedReader()
                do {
                    val header = reader.readLine()
                        ?: throw IOException("Client disconnected before sending request headers")
                } while (header.isNotEmpty())
                requestReceived.complete(Unit)
                if (reader.read() == -1) {
                    peerDisconnected.complete(Unit)
                }
            }
        }
        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        callCancelled.countDown()
                    }
                }
            )
            .readTimeout(TEST_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)
        val request = async(Dispatchers.IO) {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "GET",
                    url = "http://127.0.0.1:${server.localPort}/stalled"
                )
            )
        }

        try {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                requestReceived.await()
            }

            withTimeout(TEST_PROMPT_CANCELLATION_MILLIS) {
                request.cancelAndJoin()
            }

            assertTrue(request.isCancelled)
            assertTrue(callCancelled.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            withTimeout(TEST_TIMEOUT_MILLIS) {
                peerDisconnected.await()
                while (client.dispatcher.runningCallsCount() != 0) {
                    delay(DISPATCHER_POLL_MILLIS)
                }
            }
            assertEquals(0, client.dispatcher.runningCallsCount())
        } finally {
            request.cancel()
            acceptedSocket.getAndSet(null)?.close()
            server.close()
            serverJob.cancel()
            withTimeout(TEST_TIMEOUT_MILLIS) {
                request.join()
                serverJob.join()
            }
        }
    }

    @Test
    fun `cancelling OkHttp transport cancels call promptly and closes racing response`() = runBlocking {
        val responseReadStarted = CountDownLatch(1)
        val releaseResponseRead = CountDownLatch(1)
        val callCancelled = CountDownLatch(1)
        val responseBody = CloseTrackingResponseBody(
            bytes = "late-response".toByteArray(),
            readStarted = responseReadStarted,
            releaseRead = releaseResponseRead
        )
        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        callCancelled.countDown()
                    }
                }
            )
            .addInterceptor { chain -> response(chain.request(), responseBody) }
            .build()
        val transport = OkHttpGoogleDriveHttpTransport(client)
        val request = async(Dispatchers.IO) {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "GET",
                    url = "https://example.invalid/cancel"
                )
            )
        }

        try {
            assertTrue(responseReadStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            request.cancel()
            withTimeout(TEST_PROMPT_CANCELLATION_MILLIS) {
                request.join()
            }

            assertTrue(request.isCancelled)
            assertTrue(callCancelled.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            releaseResponseRead.countDown()
            assertTrue(responseBody.awaitClosed())
            request.cancel()
            withTimeout(TEST_TIMEOUT_MILLIS) {
                request.join()
            }
        }
    }

    private fun storage(transport: RecordingDriveTransport): GoogleDrivePasskeyBackupCloudStorage {
        return GoogleDrivePasskeyBackupCloudStorage(
            GoogleDrivePasskeyBackupDriveClient(
                accessTokenProvider = GoogleDriveAccessTokenProvider { "token-123" },
                transport = transport
            )
        )
    }

    private fun payload(
        storageKey: String = "wallet-1234",
        walletId: String = "wallet-001",
        accountName: String = "alice@example.com",
        createdAtMillis: Long = 1_767_225_600_000L,
        encryptedPayload: ByteArray = validTestEnvelope(
            storageKey = storageKey,
            walletId = walletId,
            accountName = accountName,
            createdAtMillis = createdAtMillis
        )
    ): PasskeyBackupEncryptedPayload {
        return PasskeyBackupEncryptedPayload(
            storageKey = storageKey,
            walletId = walletId,
            accountName = accountName,
            createdAtMillis = createdAtMillis,
            encryptedPayload = encryptedPayload
        )
    }

    private fun fileListJson(
        storageKey: String = "wallet-1234",
        walletId: String = "wallet-001",
        accountName: String = "alice@example.com",
        createdAtMillis: String = "1767225600000",
        schemaVersion: String = "1"
    ): String {
        return """
            {
              "files": [
                {
                  "id": "file-1",
                  "appProperties": {
                    "storageKey": "$storageKey",
                    "walletId": "$walletId",
                    "accountName": "$accountName",
                    "createdAtMillis": "$createdAtMillis",
                    "schemaVersion": "$schemaVersion"
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun jsonResponse(body: String): GoogleDriveHttpResponse {
        return GoogleDriveHttpResponse(code = 200, body = body.toByteArray())
    }

    private fun GoogleDriveHttpRequest.bodyText(): String {
        return body?.toString(Charsets.UTF_8).orEmpty()
    }

    private fun response(request: Request, body: ResponseBody): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private class CloseTrackingResponseBody(
        bytes: ByteArray,
        private val declaredLength: Long = bytes.size.toLong(),
        private val readStarted: CountDownLatch? = null,
        private val releaseRead: CountDownLatch? = null,
        private val readFailure: IOException? = null
    ) : ResponseBody() {
        private val closed = CountDownLatch(1)
        private val source = object : Source {
            private val buffer = Buffer().write(bytes)

            override fun read(sink: Buffer, byteCount: Long): Long {
                readStarted?.countDown()
                if (releaseRead?.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) == false) {
                    throw IOException("Timed out waiting to release response body")
                }
                readFailure?.let { throw it }
                return buffer.read(sink, byteCount)
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                buffer.close()
                closed.countDown()
            }
        }.buffer()

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = declaredLength

        override fun source(): BufferedSource = source

        fun awaitClosed(): Boolean = closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private class RecordingDriveTransport(
        vararg responses: GoogleDriveHttpResponse
    ) : GoogleDriveHttpTransport {
        val requests = mutableListOf<GoogleDriveHttpRequest>()
        private val responses = ArrayDeque(responses.toList())

        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            return responses.removeFirstOrNull() ?: error("Unexpected request: $request")
        }
    }

    private companion object {
        const val DISPATCHER_POLL_MILLIS = 10L
        const val TEST_PROMPT_CANCELLATION_MILLIS = 1_000L
        const val TEST_TIMEOUT_MILLIS = 5_000L
        const val TEST_TIMEOUT_SECONDS = 5L
    }
}
