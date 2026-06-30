package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDrivePasskeyBackupCloudStorageTest {
    @Test
    fun `save creates appDataFolder multipart upload when backup does not exist`() = runBlocking {
        val transport = RecordingDriveTransport(
            jsonResponse("""{"files":[]}"""),
            jsonResponse("""{"id":"drive-file"}""")
        )
        val storage = storage(transport)

        storage.savePasskeyBackup(payload(encryptedPayload = "ABC".toByteArray()))

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
        assertTrue(uploadBody.contains("ABC"))
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
        val transport = RecordingDriveTransport(
            jsonResponse(fileListJson()),
            GoogleDriveHttpResponse(code = 200, body = byteArrayOf(7, 8, 9))
        )
        val storage = storage(transport)

        val loaded = storage.loadPasskeyBackup(" wallet-1234 ")

        assertEquals("wallet-1234", loaded?.storageKey)
        assertEquals("wallet-001", loaded?.walletId)
        assertEquals("alice@example.com", loaded?.accountName)
        assertEquals(1_767_225_600_000L, loaded?.createdAtMillis)
        assertArrayEquals(byteArrayOf(7, 8, 9), loaded?.encryptedPayload)
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
        encryptedPayload: ByteArray = byteArrayOf(1, 2, 3)
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
}
