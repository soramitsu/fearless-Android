package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupChallengeServiceTest {
    @Test
    fun `registration challenge posts wallet metadata and validates response`() = runBlocking {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(16) { (it + 32).toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "${base64Url(challenge)}",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service("${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/", transport)

        val result = service.registrationChallenge(
            walletId = " wallet-001 ",
            accountName = " alice@example.com ",
            displayName = " Alice "
        )

        assertEquals("registration-1234", result.registrationId)
        assertArrayEquals(challenge, result.challenge)
        assertArrayEquals(userId, result.userId)
        assertEquals("alice@example.com", result.userName)
        assertEquals("Alice", result.displayName)
        assertEquals("wallet-1234", result.storageKey)

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/registration/challenge",
            request.url
        )
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])

        val body = request.jsonBody()
        assertEquals("wallet-001", body.get("walletId").asString)
        assertEquals("alice@example.com", body.get("accountName").asString)
        assertEquals("Alice", body.get("displayName").asString)
        assertEquals(PasskeyBackupContract.PASSKEY_RP_ID, body.get("rpId").asString)
        assertEquals(PasskeyBackupContract.SCHEMA_VERSION, body.get("schemaVersion").asInt)
    }

    @Test
    fun `complete registration posts credential object and returns storage key`() = runBlocking {
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        val result = service.completeRegistration(
            registrationId = " registration-1234 ",
            credentialResponseJson = """{"id":"cred-1","response":{"clientDataJSON":"abc"}}"""
        )

        assertEquals("wallet-1234", result.storageKey)
        val request = transport.requests.single()
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/registration/complete",
            request.url
        )
        val body = request.jsonBody()
        assertEquals("registration-1234", body.get("registrationId").asString)
        assertEquals(PasskeyBackupContract.PASSKEY_RP_ID, body.get("rpId").asString)
        assertEquals("cred-1", body.getAsJsonObject("credential").get("id").asString)
    }

    @Test
    fun `assertion challenge posts storage key and rejects mismatched response keys`() = runBlocking {
        val challenge = ByteArray(32) { (it + 1).toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "assertionId": "assertion-1234",
                  "challenge": "${base64Url(challenge)}",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        val result = service.assertionChallenge(" wallet-1234 ")

        assertEquals("assertion-1234", result.assertionId)
        assertArrayEquals(challenge, result.challenge)
        assertEquals("wallet-1234", result.storageKey)

        val request = transport.requests.single()
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/assertion/challenge",
            request.url
        )
        val body = request.jsonBody()
        assertEquals("wallet-1234", body.get("storageKey").asString)
        assertEquals(PasskeyBackupContract.PASSKEY_RP_ID, body.get("rpId").asString)
        assertEquals(PasskeyBackupContract.SCHEMA_VERSION, body.get("schemaVersion").asInt)
    }

    @Test
    fun `complete assertion posts credential object`() = runBlocking {
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        val result = service.completeAssertion(
            assertionId = " assertion-1234 ",
            credentialResponseJson = """{"id":"cred-1","response":{"authenticatorData":"abc"}}"""
        )

        assertEquals("wallet-1234", result.storageKey)
        val request = transport.requests.single()
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/assertion/complete",
            request.url
        )
        val body = request.jsonBody()
        assertEquals("assertion-1234", body.get("assertionId").asString)
        assertEquals("cred-1", body.getAsJsonObject("credential").get("id").asString)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service requires HTTPS base URL`() {
        service(baseUrl = "http://backup.fearlesswallet.io")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects query in base URL`() {
        service(baseUrl = "https://backup.fearlesswallet.io?env=dev")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects blank wallet id before network`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        runBlocking {
            service.registrationChallenge(walletId = " ", accountName = "alice@example.com", displayName = "Alice")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects invalid account name before network`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        try {
            runBlocking {
                service.registrationChallenge(
                    walletId = "wallet-001",
                    accountName = "alice example.com",
                    displayName = "Alice"
                )
            }
        } finally {
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects invalid ceremony id before network`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        runBlocking {
            service.completeRegistration(
                registrationId = "../registration",
                credentialResponseJson = """{"id":"cred-1"}"""
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects credential arrays before network`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        runBlocking {
            service.completeAssertion(
                assertionId = "assertion-1234",
                credentialResponseJson = """["not-an-object"]"""
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertion challenge rejects invalid storage key before network`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        runBlocking {
            service.assertionChallenge("../wallet")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertion challenge rejects mismatched response storage key`() {
        val challenge = ByteArray(32) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "assertionId": "assertion-1234",
                  "challenge": "${base64Url(challenge)}",
                  "storageKey": "wallet-5678",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        runBlocking {
            service.assertionChallenge("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration challenge rejects wrong relying party`() {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(16) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "${base64Url(challenge)}",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "example.com",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        runBlocking {
            service.registrationChallenge("wallet-001", "alice@example.com", "Alice")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration challenge rejects unsupported schema version`() {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(16) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "${base64Url(challenge)}",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION + 1}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        runBlocking {
            service.registrationChallenge("wallet-001", "alice@example.com", "Alice")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration challenge rejects invalid base64url challenge`() {
        val userId = ByteArray(16) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "not base64url",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        runBlocking {
            service.registrationChallenge("wallet-001", "alice@example.com", "Alice")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration challenge rejects short decoded user id`() {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(15) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "${base64Url(challenge)}",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val service = service(transport = transport)

        runBlocking {
            service.registrationChallenge("wallet-001", "alice@example.com", "Alice")
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `challenge service rejects malformed JSON responses`() {
        val transport = RecordingChallengeTransport(
            GoogleDriveHttpResponse(code = 200, body = """{"not":""".toByteArray())
        )
        val service = service(transport = transport)

        runBlocking {
            service.assertionChallenge("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service rejects empty responses`() {
        val transport = RecordingChallengeTransport(GoogleDriveHttpResponse(code = 200, body = ByteArray(0)))
        val service = service(transport = transport)

        runBlocking {
            service.assertionChallenge("wallet-1234")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `challenge service fails closed on non success HTTP responses`() {
        val transport = RecordingChallengeTransport(GoogleDriveHttpResponse(code = 503, body = ByteArray(0)))
        val service = service(transport = transport)

        runBlocking {
            service.assertionChallenge("wallet-1234")
        }
    }

    @Test
    fun `invalid local inputs do not call challenge transport`() {
        val transport = RecordingChallengeTransport()
        val service = service(transport = transport)

        runCatching {
            runBlocking {
                service.completeAssertion(assertionId = "../assertion", credentialResponseJson = """{"id":"cred"}""")
            }
        }

        assertTrue(transport.requests.isEmpty())
    }

    private fun service(
        baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
        transport: RecordingChallengeTransport = RecordingChallengeTransport()
    ): HttpPasskeyBackupChallengeService {
        return HttpPasskeyBackupChallengeService(baseUrl = baseUrl, transport = transport)
    }

    private fun jsonResponse(body: String): GoogleDriveHttpResponse {
        return GoogleDriveHttpResponse(code = 200, body = body.toByteArray())
    }

    private fun base64Url(value: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }

    private fun GoogleDriveHttpRequest.jsonBody(): JsonObject {
        return JsonParser.parseString(bodyText()).asJsonObject
    }

    private fun GoogleDriveHttpRequest.bodyText(): String {
        return body?.toString(Charsets.UTF_8).orEmpty()
    }

    private class RecordingChallengeTransport(
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
