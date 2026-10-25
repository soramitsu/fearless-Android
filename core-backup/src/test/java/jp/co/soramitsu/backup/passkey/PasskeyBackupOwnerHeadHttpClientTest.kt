package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupOwnerHeadHttpClientTest {
    private var nowMillis = 1_700_000_000_000L

    @Test
    fun `reads exact session-bound owner head and sends only a metadata query`() = runBlocking {
        val transport = RecordingTransport(response(headBody()))
        val result = client(transport, enabled = true).readHead(session())

        assertEquals(SUBJECT, result.ownerSubject)
        assertEquals(NAMESPACE, result.backupNamespace)
        assertEquals(CURRENT_GENERATION, result.head?.generationId)
        assertEquals(PREVIOUS_GENERATION, result.previous?.generationId)
        assertEquals("current-drive-file", result.currentReadParameters().fileId)
        assertEquals(CURRENT_DIGEST, result.currentReadParameters().sha256)
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/owner/backup/head",
            request.url
        )
        assertEquals("Bearer $SESSION_TOKEN", request.headers["Authorization"])
        assertEquals("no-store", request.headers["Cache-Control"])
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals("{\"schemaVersion\":1}", request.bodyText())
        assertEquals(8 * 1024, request.maxResponseBytes)
        assertFalse(request.isOneShot)
        assertFalse(request.toString().contains(SESSION_TOKEN))
        assertFalse(request.bodyText().contains("prf"))
        assertFalse(request.bodyText().contains(ACCOUNT_BINDING))
        assertFalse(request.headers.values.any { it.contains("drive-access-token") })
        assertEquals("PasskeyBackupAuthenticatedHead(redacted)", result.toString())
    }

    @Test
    fun `accepts empty authority namespace and first committed generation`() = runBlocking {
        val empty = client(RecordingTransport(response(headBody(head = null, previous = null))), true)
            .readHead(session())
        assertEquals(null, empty.head)
        assertEquals(null, empty.previous)

        val first = client(RecordingTransport(response(headBody(head = descriptor(1, 0, null), previous = null))), true)
            .readHead(session())
        assertEquals(1L, first.head?.headRevision)
        assertEquals(null, first.previous)
    }

    @Test
    fun `rejects duplicate coerced substituted and noncanonical head responses`() {
        val normal = headBody()
        val bad = listOf(
            normal.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            normal.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            normal.replace("\"ownerSubject\":", "\"\\u006fwnerSubject\":\"bogus\",\"ownerSubject\":"),
            normal.replace("\"bundleSha256\":\"$CURRENT_DIGEST\"", "\"bundleSha256\":\"bad\",\"bundleSha256\":\"$CURRENT_DIGEST\""),
            normal.replace("\"ownerSubject\":", "\"unexpected\":null,\"ownerSubject\":"),
            normal.replace("\"ownerSubject\":\"$SUBJECT\"", "\"ownerSubject\":\"owner:${base64Url(32, 9)}\""),
            normal.replace("\"backupNamespace\":\"$NAMESPACE\"", "\"backupNamespace\":\"backup:${base64Url(32, 9)}\""),
            normal.replace("\"storageAccountBinding\":\"$ACCOUNT_BINDING\"", "\"storageAccountBinding\":\"${"0".repeat(64)}\""),
            normal.replace("\"headRevision\":\"2\"", "\"headRevision\":\"02\""),
            normal.replace("\"headRevision\":\"2\"", "\"headRevision\":2"),
            normal.replace("\"keyEpoch\":\"1\"", "\"keyEpoch\":\"9007199254740992\""),
            normal.replace("\"parentHeadSha256\":\"$PREVIOUS_DIGEST\"", "\"parentHeadSha256\":null"),
            normal.replace("\"previous\":", "\"previous\":{\"nested\":{}},\"previous\":"),
            normal.replace("\"previous\":", "\"previous\":true,\"previous\":"),
            normal.dropLast(1) + ",\"head\":null}",
            normal + "{}"
        )
        bad.forEachIndexed { index, body ->
            assertTrue("case $index", attempt(response(body)).isFailure)
        }
        assertTrue(attempt(GoogleDriveHttpResponse(200, byteArrayOf(0xC3.toByte(), 0x28))).isFailure)
        assertTrue(attempt(response("x".repeat(8 * 1024 + 1))).isFailure)
        assertTrue(attempt(GoogleDriveHttpResponse(401, headBody().toByteArray())).isFailure)
    }

    @Test
    fun `rejects missing predecessor changed digest and duplicate file identity`() {
        val noPrevious = headBody(previous = null)
        val wrongDigest = headBody(head = descriptor(2, 1, "c".repeat(64)))
        val sameFile = headBody(previous = descriptor(1, 0, null, fileId = "current-drive-file"))
        assertTrue(attempt(response(noPrevious)).isFailure)
        assertTrue(attempt(response(wrongDigest)).isFailure)
        assertTrue(attempt(response(sameFile)).isFailure)
    }

    @Test
    fun `disabled expired substituted and cancelled sessions never use the head`() {
        val disabled = RecordingTransport(response(headBody()))
        assertTrue(runCatching { runBlocking { client(disabled, false).readHead(session()) } }.isFailure)
        assertTrue(disabled.requests.isEmpty())
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)

        val invalid = listOf(
            session(token = "session.invalid"),
            session(subject = "owner:invalid"),
            session(platform = "ios"),
            session(expiresAt = nowMillis / 1_000L),
            session(expiresAt = nowMillis / 1_000L + 661),
            session(generation = -1)
        )
        invalid.forEachIndexed { index, session ->
            val transport = RecordingTransport(response(headBody()))
            assertTrue(
                "case $index",
                runCatching {
                    runBlocking { client(transport, true).readHead(session) }
                }.isFailure
            )
            assertTrue("case $index", transport.requests.isEmpty())
        }

        val invalidAccount = RecordingTransport(response(headBody()))
        val unavailableAccount = GoogleDriveAccessTokenProvider { error("Drive account unavailable") }
        assertTrue(
            runCatching {
                runBlocking { client(invalidAccount, true, unavailableAccount).readHead(session()) }
            }.isFailure
        )
        assertTrue(invalidAccount.requests.isEmpty())

        val switchedProvider = RecordingTokenProvider()
        val switchedAccount = RecordingTransport(response(headBody())) {
            switchedProvider.subject = "other-google-subject"
        }
        assertTrue(
            runCatching {
                runBlocking { client(switchedAccount, true, switchedProvider).readHead(session()) }
            }.isFailure
        )
        assertEquals(1, switchedAccount.requests.size)
        assertEquals(2, switchedProvider.calls)

        val expiresInFlight = RecordingTransport(response(headBody())) { nowMillis += 700_000L }
        assertTrue(
            runCatching {
                runBlocking { client(expiresInFlight, true).readHead(session()) }
            }.isFailure
        )
        assertEquals(1, expiresInFlight.requests.size)

        val cancelled = RecordingTransport(response(headBody())) { throw CancellationException("cancelled") }
        val cancelledError = runCatching {
            runBlocking { client(cancelled, true).readHead(session()) }
        }.exceptionOrNull()
        assertTrue(cancelledError is CancellationException)
        assertEquals(1, cancelled.requests.size)
    }

    private fun attempt(response: GoogleDriveHttpResponse) = runCatching {
        runBlocking { client(RecordingTransport(response), true).readHead(session()) }
    }

    private fun client(
        transport: GoogleDriveHttpTransport,
        enabled: Boolean,
        tokenProvider: GoogleDriveAccessTokenProvider = RecordingTokenProvider()
    ) = PasskeyBackupOwnerHeadHttpClient(
        tokenProvider = tokenProvider,
        transport = transport,
        nowMillis = { nowMillis },
        isReleaseEnabled = enabled
    )

    private fun session(
        token: String = SESSION_TOKEN,
        subject: String = SUBJECT,
        platform: String = "android",
        expiresAt: Long = nowMillis / 1_000L + 599L,
        generation: Long = 0
    ) = PasskeyBackupOwnerSession(token, subject, NAMESPACE, generation, platform, expiresAt)

    private fun headBody(
        head: JsonObject? = descriptor(2, 1, PREVIOUS_DIGEST),
        previous: JsonObject? = descriptor(1, 0, null, PREVIOUS_GENERATION, PREVIOUS_DIGEST, "previous-drive-file")
    ) = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("ownerSubject", SUBJECT)
        addProperty("backupNamespace", NAMESPACE)
        add("head", head)
        add("previous", previous)
    }.toString()

    private fun descriptor(
        revision: Int,
        parentRevision: Int,
        parentDigest: String?,
        generationId: String = CURRENT_GENERATION,
        bundleDigest: String = CURRENT_DIGEST,
        fileId: String = "current-drive-file"
    ) = JsonObject().apply {
        addProperty("headRevision", revision.toString())
        addProperty("parentHeadRevision", parentRevision.toString())
        if (parentDigest == null) add("parentHeadSha256", null) else addProperty("parentHeadSha256", parentDigest)
        addProperty("generationId", generationId)
        addProperty("bundleSha256", bundleDigest)
        addProperty("keyEpoch", "1")
        addProperty("driveFileId", fileId)
        addProperty("storageAccountBinding", ACCOUNT_BINDING)
    }

    private fun response(body: String) = GoogleDriveHttpResponse(200, body.toByteArray(Charsets.UTF_8))

    private class RecordingTransport(
        private val response: GoogleDriveHttpResponse,
        private val beforeReturn: () -> Unit = {}
    ) : GoogleDriveHttpTransport {
        val requests = mutableListOf<GoogleDriveHttpRequest>()

        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            beforeReturn()
            return response
        }
    }

    private class RecordingTokenProvider(var subject: String = GOOGLE_SUBJECT) : GoogleDriveAccessTokenProvider {
        var calls = 0

        override suspend fun accessToken(): GoogleDriveAccountAccess {
            calls++
            return GoogleDriveAccountAccess(subject, "user@example.com", "drive-access-token")
        }
    }

    private fun GoogleDriveHttpRequest.bodyText() = requireNotNull(body).toString(Charsets.UTF_8)

    private companion object {
        val SESSION_TOKEN = "session.${base64Url(32, 1)}"
        val SUBJECT = "owner:${base64Url(32, 2)}"
        val NAMESPACE = "backup:${base64Url(32, 3)}"
        val CURRENT_GENERATION = base64Url(32, 4)
        val PREVIOUS_GENERATION = base64Url(32, 5)
        val CURRENT_DIGEST = "a".repeat(64)
        val PREVIOUS_DIGEST = "b".repeat(64)
        const val GOOGLE_SUBJECT = "selected-google-subject"
        val ACCOUNT_BINDING = PasskeyBackupGenerationFormat.storageAccountBinding(GOOGLE_SUBJECT)

        fun base64Url(length: Int, fill: Byte) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(length) { fill })
    }
}
