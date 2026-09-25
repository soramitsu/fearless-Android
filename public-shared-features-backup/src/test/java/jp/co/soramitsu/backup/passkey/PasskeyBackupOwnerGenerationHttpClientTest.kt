package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupOwnerGenerationHttpClientTest {
    private var nowMillis = 1_700_000_000_000L

    @Test
    fun `grant commit and status use exact metadata routes without Drive or PRF secrets`() = runBlocking {
        val transport =
            RecordingTransport(
                grantResponse(),
                committedResponse(),
                committedResponse(),
                response("{\"status\":\"absent\"}"),
            )
        val client = client(transport)
        val session = session()
        val metadata = metadata()

        val grant = client.grant(session, metadata)
        assertEquals(nowMillis / 1_000L + 59, grant.expiresAt)
        val committed = client.commit(session, metadata, grant)
        assertEquals(GENERATION_ID, committed.generationId)
        assertEquals(1L, committed.headRevision)
        val status = client.operationStatus(session, metadata)
        assertTrue(status is PasskeyBackupGenerationOperationStatus.Committed)
        assertEquals(GENERATION_ID, (status as PasskeyBackupGenerationOperationStatus.Committed).descriptor.generationId)
        assertEquals(PasskeyBackupGenerationOperationStatus.Absent, client.operationStatus(session, metadata))

        assertEquals(4, transport.requests.size)
        val grantRequest = transport.requests[0]
        assertEquals("POST", grantRequest.method)
        assertEquals("${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}$GRANT_PATH", grantRequest.url)
        assertEquals("Bearer $SESSION_TOKEN", grantRequest.headers["Authorization"])
        assertEquals(null, grantRequest.headers["X-Passkey-Owner-Session"])
        assertEquals("application/json; charset=utf-8", grantRequest.headers["Content-Type"])
        assertEquals("no-store", grantRequest.headers["Cache-Control"])
        assertEquals(8 * 1024, grantRequest.maxResponseBytes)
        assertFalse(grantRequest.isOneShot)
        val fields = JsonParser.parseString(grantRequest.bodyText()).asJsonObject
        assertEquals(
            setOf(
                "schemaVersion", "operationId", "generationId", "backupNamespace", "expectedHeadRevision",
                "expectedHeadSha256", "bundleSha256", "keyEpoch", "driveFileId", "storageAccountBinding",
            ),
            fields.keySet(),
        )
        assertEquals("0", fields["expectedHeadRevision"].asString)
        assertTrue(fields["expectedHeadSha256"].isJsonNull)
        assertEquals(ACCOUNT_BINDING, fields["storageAccountBinding"].asString)
        assertEquals("${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}$COMMIT_PATH", transport.requests[1].url)
        assertEquals("Bearer $GRANT_TOKEN", transport.requests[1].headers["Authorization"])
        assertEquals(SESSION_TOKEN, transport.requests[1].headers["X-Passkey-Owner-Session"])
        assertTrue(transport.requests[1].isOneShot)
        assertEquals(grantRequest.bodyText(), transport.requests[1].bodyText())
        assertEquals("${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}$OPERATION_PATH", transport.requests[2].url)
        assertEquals("Bearer $SESSION_TOKEN", transport.requests[2].headers["Authorization"])
        assertEquals("{\"schemaVersion\":1,\"operationId\":\"$OPERATION_ID\"}", transport.requests[2].bodyText())
        transport.requests.forEach { request ->
            assertFalse(request.bodyText().contains("prf", ignoreCase = true))
            assertFalse(request.bodyText().contains("drive-access-token"))
            assertFalse(request.headers.values.any { it.contains("drive-access-token") })
            assertFalse(request.toString().contains(SESSION_TOKEN))
            assertFalse(request.toString().contains(GRANT_TOKEN))
        }
        assertEquals("PasskeyBackupGenerationMetadata(redacted)", metadata.toString())
        assertEquals("PasskeyBackupGenerationGrant(redacted)", grant.toString())
    }

    @Test
    fun `strict parser rejects substituted and malformed grant and operation responses`() {
        val malformedGrants =
            listOf(
                "{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":${nowMillis / 1_000L + 59},\"token\":\"$GRANT_TOKEN\"}",
                "{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":\"${nowMillis / 1_000L + 59}\"}",
                "{\"token\":\"grant.invalid\",\"expiresAt\":${nowMillis / 1_000L + 59}}",
                "{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":${nowMillis / 1_000L + 120}}",
                "{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":${nowMillis / 1_000L + 59},\"prf\":null}",
                "{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":${nowMillis / 1_000L + 59}}{}",
            )
        malformedGrants.forEachIndexed { index, body ->
            assertTrue(
                "grant $index",
                runCatching {
                    runBlocking { client(RecordingTransport(response(body))).grant(session(), metadata()) }
                }.isFailure,
            )
        }
        assertTrue(
            runCatching {
                runBlocking {
                    client(RecordingTransport(GoogleDriveHttpResponse(200, byteArrayOf(0xC3.toByte(), 0x28))))
                        .grant(session(), metadata())
                }
            }.isFailure,
        )

        val good = committedResponseBody()
        val malformedStatus =
            listOf(
                good.replace("\"status\":\"committed\"", "\"status\":\"absent\""),
                good.replace("\"status\":\"committed\"", "\"status\":\"committed\",\"status\":\"absent\""),
                good.replace("\"status\":", "\"\\u0073tatus\":\"absent\",\"status\":"),
                good.replace("\"generationId\":\"$GENERATION_ID\"", "\"generationId\":\"$OTHER_ID\""),
                good.replace("\"storageAccountBinding\":\"$ACCOUNT_BINDING\"", "\"storageAccountBinding\":\"${"0".repeat(64)}\""),
                good.replace("\"headRevision\":\"1\"", "\"headRevision\":1"),
                good.replace("\"keyEpoch\":\"1\"", "\"keyEpoch\":\"9007199254740992\""),
                good.replace("\"descriptor\":", "\"unexpected\":null,\"descriptor\":"),
                good + "{}",
                "x".repeat(8 * 1024 + 1),
            )
        malformedStatus.forEachIndexed { index, body ->
            assertTrue(
                "status $index",
                runCatching {
                    runBlocking { client(RecordingTransport(response(body))).operationStatus(session(), metadata()) }
                }.isFailure,
            )
        }
        assertTrue(
            runCatching {
                runBlocking {
                    client(RecordingTransport(response("{\"status\":\"absent\"}"))).commit(
                        session(),
                        metadata(),
                        issuedGrant(),
                    )
                }
            }.isFailure,
        )
    }

    @Test
    fun `disabled expired wrong-owner and wrong-account requests never reach transport`() {
        val disabled = RecordingTransport(grantResponse())
        assertTrue(runCatching { runBlocking { client(disabled, false).grant(session(), metadata()) } }.isFailure)
        assertTrue(disabled.requests.isEmpty())
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)

        val expired = RecordingTransport(grantResponse())
        assertTrue(
            runCatching {
                runBlocking { client(expired).grant(session(expiresAt = nowMillis / 1_000L), metadata()) }
            }.isFailure,
        )
        assertTrue(expired.requests.isEmpty())

        val wrongOwner = RecordingTransport(grantResponse())
        assertTrue(
            runCatching {
                runBlocking { client(wrongOwner).grant(session(subject = "owner:$OTHER_ID"), metadata()) }
            }.isFailure,
        )
        assertTrue(wrongOwner.requests.isEmpty())

        val wrongAccount = RecordingTransport(grantResponse())
        assertTrue(
            runCatching {
                runBlocking {
                    client(wrongAccount, provider = RecordingTokenProvider("other-google-subject"))
                        .grant(session(), metadata())
                }
            }.isFailure,
        )
        assertTrue(wrongAccount.requests.isEmpty())
    }

    @Test
    fun `account switch and expiry during metadata calls deny success without leaking secrets`() {
        val switchedProvider = RecordingTokenProvider()
        val switched = RecordingTransport(grantResponse()) { switchedProvider.subject = "other-google-subject" }
        assertTrue(
            runCatching {
                runBlocking { client(switched, provider = switchedProvider).grant(session(), metadata()) }
            }.isFailure,
        )
        assertEquals(1, switched.requests.size)
        assertEquals(2, switchedProvider.calls)

        val expiring = RecordingTransport(grantResponse()) { nowMillis += 700_000L }
        assertTrue(
            runCatching {
                runBlocking { client(expiring).grant(session(), metadata()) }
            }.isFailure,
        )
        assertEquals(1, expiring.requests.size)

        val lateExpiryProvider = RecordingTokenProvider(onAccess = { calls ->
            if (calls == 2) nowMillis += 700_000L
        })
        val lateExpiry = RecordingTransport(grantResponse())
        assertTrue(
            runCatching {
                runBlocking { client(lateExpiry, provider = lateExpiryProvider).grant(session(), metadata()) }
            }.isFailure,
        )
        assertEquals(1, lateExpiry.requests.size)
        assertEquals(2, lateExpiryProvider.calls)

        val cancelled = RecordingTransport(grantResponse()) { throw CancellationException("cancelled") }
        val error =
            runCatching {
                runBlocking { client(cancelled).grant(session(), metadata()) }
            }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test
    fun `exact grant and candidate binding rejects substitution before commit`() = runBlocking {
        val transport = RecordingTransport(grantResponse(), committedResponse())
        val client = client(transport)
        val granted = client.grant(session(), metadata())
        assertTrue(
            runCatching {
                runBlocking { client.commit(session(), metadata(bundleSha256 = "c".repeat(64)), granted) }
            }.isFailure,
        )
        assertTrue(
            runCatching {
                runBlocking { client.commit(session(token = "session.$OTHER_ID"), metadata(), granted) }
            }.isFailure,
        )
        assertEquals(1, transport.requests.size)
        nowMillis += 61_000L
        assertTrue(
            runCatching {
                runBlocking { client.commit(session(), metadata(), granted) }
            }.isFailure,
        )
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `candidate must extend the selected authenticated head`() {
        val wrongParent =
            PasskeyBackupGeneration.Context(
                SUBJECT,
                NAMESPACE,
                GENERATION_ID,
                1,
                "a".repeat(64),
                1,
                ACCOUNT_BINDING,
            )
        assertTrue(
            runCatching {
                PasskeyBackupGenerationMetadata(OPERATION_ID, wrongParent, BUNDLE_DIGEST, FILE_ID, emptyHead())
            }.isFailure,
        )
        assertTrue(
            runCatching {
                PasskeyBackupGenerationMetadata(GENERATION_ID, context(), BUNDLE_DIGEST, FILE_ID, emptyHead())
            }.isFailure,
        )
        assertTrue(
            runCatching {
                PasskeyBackupGenerationMetadata(OPERATION_ID, context(), BUNDLE_DIGEST, "wrong/file", emptyHead())
            }.isFailure,
        )

        val first = PasskeyBackupHeadDescriptor(1, 0, null, OTHER_ID, "a".repeat(64), 1, "old-drive", ACCOUNT_BINDING)
        val head = PasskeyBackupAuthenticatedHead(SUBJECT, NAMESPACE, first, null, SUBJECT, NAMESPACE, ACCOUNT_BINDING)
        val secondContext =
            PasskeyBackupGeneration.Context(
                SUBJECT,
                NAMESPACE,
                GENERATION_ID,
                1,
                "a".repeat(64),
                1,
                ACCOUNT_BINDING,
            )
        val second = PasskeyBackupGenerationMetadata(OPERATION_ID, secondContext, BUNDLE_DIGEST, FILE_ID, head)
        assertEquals(1L, second.expectedHeadRevision)
        assertEquals("a".repeat(64), second.expectedHeadSha256)

        val otherBinding = "f".repeat(64)
        val otherFirst = PasskeyBackupHeadDescriptor(1, 0, null, OTHER_ID, "a".repeat(64), 1, "old-drive", otherBinding)
        val otherHead = PasskeyBackupAuthenticatedHead(SUBJECT, NAMESPACE, otherFirst, null, SUBJECT, NAMESPACE, otherBinding)
        assertTrue(
            runCatching {
                PasskeyBackupGenerationMetadata(OPERATION_ID, secondContext, BUNDLE_DIGEST, FILE_ID, otherHead)
            }.isFailure,
        )
    }

    private fun client(
        transport: RecordingTransport,
        enabled: Boolean = true,
        provider: RecordingTokenProvider = RecordingTokenProvider(),
    ) = PasskeyBackupOwnerGenerationHttpClient(
        tokenProvider = provider,
        transport = transport,
        nowMillis = { nowMillis },
        isReleaseEnabled = enabled,
    )

    private fun session(
        token: String = SESSION_TOKEN,
        subject: String = SUBJECT,
        expiresAt: Long = nowMillis / 1_000L + 599,
    ) = PasskeyBackupOwnerSession(token, subject, NAMESPACE, 0, "android", expiresAt)

    private fun context() = PasskeyBackupGeneration.Context(
        SUBJECT,
        NAMESPACE,
        GENERATION_ID,
        0,
        null,
        1,
        ACCOUNT_BINDING,
    )

    private fun emptyHead() = PasskeyBackupAuthenticatedHead(
        SUBJECT,
        NAMESPACE,
        null,
        null,
        SUBJECT,
        NAMESPACE,
        ACCOUNT_BINDING,
    )

    private fun metadata(bundleSha256: String = BUNDLE_DIGEST) = PasskeyBackupGenerationMetadata(
        OPERATION_ID,
        context(),
        bundleSha256,
        FILE_ID,
        emptyHead(),
    )

    private fun issuedGrant() = PasskeyBackupGenerationGrant(
        GRANT_TOKEN,
        nowMillis / 1_000L + 59,
        SESSION_TOKEN,
        metadata().digest(),
    )

    private fun grantResponse() = response("{\"token\":\"$GRANT_TOKEN\",\"expiresAt\":${nowMillis / 1_000L + 59}}")

    private fun committedResponse() = response(committedResponseBody())

    private fun committedResponseBody() = JsonObject().apply {
        addProperty("status", "committed")
        add(
            "descriptor",
            JsonObject().apply {
                addProperty("headRevision", "1")
                addProperty("parentHeadRevision", "0")
                add("parentHeadSha256", JsonNull.INSTANCE)
                addProperty("generationId", GENERATION_ID)
                addProperty("bundleSha256", BUNDLE_DIGEST)
                addProperty("keyEpoch", "1")
                addProperty("driveFileId", FILE_ID)
                addProperty("storageAccountBinding", ACCOUNT_BINDING)
            },
        )
    }.toString()

    private fun response(body: String) = GoogleDriveHttpResponse(200, body.toByteArray(Charsets.UTF_8))

    private class RecordingTransport(
        vararg responses: GoogleDriveHttpResponse,
        private val onRequest: () -> Unit = {},
    ) : GoogleDriveHttpTransport {
        private val pending = ArrayDeque(responses.toList())
        val requests = mutableListOf<GoogleDriveHttpRequest>()

        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            onRequest()
            return pending.removeFirst()
        }
    }

    private class RecordingTokenProvider(
        var subject: String = GOOGLE_SUBJECT,
        private val onAccess: (Int) -> Unit = {},
    ) : GoogleDriveAccessTokenProvider {
        var calls = 0

        override suspend fun accessToken(): GoogleDriveAccountAccess {
            calls++
            onAccess(calls)
            return GoogleDriveAccountAccess(subject, "user@example.com", "drive-access-token")
        }
    }

    private fun GoogleDriveHttpRequest.bodyText() = requireNotNull(body).toString(Charsets.UTF_8)

    private companion object {
        const val GRANT_PATH = "/api/passkey-backup/v1/owner/backup/grant"
        const val COMMIT_PATH = "/api/passkey-backup/v1/owner/backup/commit"
        const val OPERATION_PATH = "/api/passkey-backup/v1/owner/backup/operation"
        val SESSION_TOKEN = "session.${base64Id(1)}"
        val GRANT_TOKEN = "grant.${base64Id(2)}"
        val SUBJECT = "owner:${base64Id(3)}"
        val NAMESPACE = "backup:${base64Id(4)}"
        val OPERATION_ID = base64Id(5)
        val GENERATION_ID = base64Id(6)
        val OTHER_ID = base64Id(7)
        const val BUNDLE_DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val GOOGLE_SUBJECT = "selected-google-subject"
        const val FILE_ID = "drive-file-one"
        val ACCOUNT_BINDING = PasskeyBackupGenerationFormat.storageAccountBinding(GOOGLE_SUBJECT)

        fun base64Id(fill: Byte): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { fill })
    }
}
