package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PasskeyBackupChallengeServiceTest {
    @Test
    fun `registration challenge posts wallet metadata and validates response`() = runBlocking {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(32) { (it + 32).toByte() }
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
        assertEquals("Bearer test.authorization-token_123", request.headers["Authorization"])

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
        assertTrue(request.isOneShot)
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
    fun `complete registration distinguishes explicit rejection from uncertain success response`() = runBlocking {
        for (status in listOf(400, 401, 403, 404, 409, 422)) {
            val explicitRejection = runCatching {
                service(
                    transport = RecordingChallengeTransport(
                        GoogleDriveHttpResponse(code = status, body = ByteArray(0))
                    )
                ).completeRegistration("registration-1234", """{"id":"credential"}""")
            }.exceptionOrNull()
            assertTrue(explicitRejection is IllegalArgumentException)
            assertTrue(explicitRejection !is PasskeyBackupRegistrationCompletionUncertainException)
        }

        for (response in listOf(
            GoogleDriveHttpResponse(code = 408, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 425, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 429, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 500, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 503, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 200, body = ByteArray(0)),
            GoogleDriveHttpResponse(code = 200, body = """{"not":"the-contract"}""".toByteArray())
        )) {
            val uncertain = runCatching {
                service(transport = RecordingChallengeTransport(response)).completeRegistration(
                    "registration-1234",
                    """{"id":"credential"}"""
                )
            }.exceptionOrNull()
            assertTrue(uncertain is PasskeyBackupRegistrationCompletionUncertainException)
        }
    }

    @Test
    fun `HTTP 500 after committed registration triggers credential compensation`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val transport = RecordingChallengeTransport(
            GoogleDriveHttpResponse(code = 500, body = ByteArray(0)),
            jsonResponse(
                """
                {
                  "storageKey": "wallet-1234",
                  "credentialId": "$credentialId",
                  "remainingCredentials": 0,
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": ${PasskeyBackupContract.SCHEMA_VERSION}
                }
                """.trimIndent()
            )
        )
        val workflow = PasskeyBackupWorkflow(
            challengeService = service(transport = transport),
            cloudBackup = object : PasskeyBackupCloudStorage {
                override suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload) {
                    error("500 completion outcome must not save a cloud backup")
                }

                override suspend fun loadPasskeyBackup(storageKey: String): PasskeyBackupEncryptedPayload? = null

                override suspend fun deletePasskeyBackup(storageKey: String) = Unit
            },
            backupKeyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            },
            isReleaseEnabled = true,
            createdAtMillisProvider = { 1_767_225_600_000L }
        )

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = PendingPasskeyBackupRegistration(
                    registrationId = "registration-1234",
                    storageKey = "wallet-1234",
                    walletId = "wallet-001",
                    accountName = "alice@example.com",
                    requestJson = "{}"
                ),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is PasskeyBackupRegistrationCompletionUncertainException)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].url.endsWith(PasskeyBackupAuthorizationRequest.REGISTRATION_COMPLETE_PATH))
        assertTrue(transport.requests[1].url.endsWith(PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH))
        assertEquals(credentialId, transport.requests[1].jsonBody().get("credentialId").asString)
    }

    @Test
    fun `registration completion one-shot body prevents OkHttp replay after committed reset`() = runBlocking {
        CommitThenResetServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
            try {
                val delegate = OkHttpGoogleDriveHttpTransport(client)
                val localTransport = object : GoogleDriveHttpTransport {
                    override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                        val productionUri = URI(request.url)
                        val localUrl = "${server.baseUrl}${productionUri.rawPath}"
                        return delegate.execute(request.copy(url = localUrl))
                    }
                }
                val error = runCatching {
                    service(transport = localTransport).completeRegistration(
                        registrationId = "registration-1234",
                        credentialResponseJson = """{"id":"Y3JlZC0x"}"""
                    )
                }.exceptionOrNull()

                server.awaitFinished()

                assertTrue(error is PasskeyBackupRegistrationCompletionUncertainException)
                assertEquals(1, server.acceptCount.get())
                assertEquals(
                    listOf("POST ${PasskeyBackupAuthorizationRequest.REGISTRATION_COMPLETE_PATH} HTTP/1.1"),
                    server.requestLines.toList()
                )
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
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
        assertFalse(request.isOneShot)
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

    @Test
    fun `credential lifecycle posts exact authorized bodies and validates responses`() = runBlocking {
        val credentialId = base64Url("credential-1".toByteArray())
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """{"storageKey":"wallet-1234","credentials":[{"id":"$credentialId","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"android","deviceType":"multiDevice","backedUp":true,"transports":["internal","hybrid"]}],"rpId":"fearlesswallet.io","schemaVersion":1}"""
            ),
            jsonResponse(
                """{"storageKey":"wallet-1234","credentialId":"$credentialId","remainingCredentials":0,"rpId":"fearlesswallet.io","schemaVersion":1}"""
            ),
            jsonResponse(
                """{"storageKey":"wallet-1234","remainingCredentials":0,"rpId":"fearlesswallet.io","schemaVersion":1}"""
            )
        )
        val authorization = RecordingAuthorizationProvider()
        val service = service(transport = transport, authorizationProvider = authorization)

        val listed = service.listCredentials(" wallet-1234 ")
        val revoked = service.revokeCredential("wallet-1234", credentialId)
        val revokedAll = service.revokeAllCredentials("wallet-1234")

        assertEquals(listOf(credentialId), listed.credentials.map { it.id })
        assertEquals(listOf("internal", "hybrid"), listed.credentials.single().transports)
        assertEquals(0, revoked.remainingCredentials)
        assertEquals(null, revokedAll.credentialId)
        assertEquals(0, revokedAll.remainingCredentials)
        assertEquals(
            listOf(
                PasskeyBackupAuthorizationRequest.CREDENTIALS_LIST_PATH,
                PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH,
                PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_ALL_PATH
            ),
            authorization.requests.map { it.path }
        )
        authorization.requests.zip(transport.requests).forEach { (grant, request) ->
            assertEquals(sha256Base64Url(requireNotNull(request.body)), grant.bodySha256)
            assertEquals("Bearer test.authorization-token_123", request.headers["Authorization"])
            val body = request.jsonBody()
            assertEquals("wallet-1234", body.get("storageKey").asString)
            assertEquals("fearlesswallet.io", body.get("rpId").asString)
            assertEquals(1, body.get("schemaVersion").asInt)
            val expectedKeys = setOf("storageKey", "rpId", "schemaVersion") +
                if (grant.path == PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH) {
                    setOf("credentialId")
                } else {
                    emptySet()
                }
            assertEquals(expectedKeys, body.keySet())
        }
    }

    @Test
    fun `credential lifecycle rejects malformed summaries and impossible counts`() {
        val credentialId = base64Url("credential-1".toByteArray())
        val validSummary =
            """{"id":"$credentialId","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"android","deviceType":"multiDevice","backedUp":true}"""
        val oversizedCredentialList = (0..32).joinToString(",") { index ->
            val id = base64Url("credential-$index".toByteArray())
            """{"id":"$id","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"android","deviceType":"multiDevice","backedUp":true}"""
        }
        val malformed = listOf(
            """{"storageKey":"wallet-1234","credentials":[{"id":"$credentialId","aaguid":"BAD","registrationPlatform":"android","deviceType":"multiDevice","backedUp":true}],"rpId":"fearlesswallet.io","schemaVersion":1}""",
            """{"storageKey":"wallet-1234","credentials":[{"id":"$credentialId","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"web","deviceType":"multiDevice","backedUp":true}],"rpId":"fearlesswallet.io","schemaVersion":1}""",
            """{"storageKey":"wallet-1234","credentials":[{"id":"$credentialId","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"android","deviceType":"singleDevice","backedUp":true}],"rpId":"fearlesswallet.io","schemaVersion":1}""",
            """{"storageKey":"wallet-1234","credentials":[{"id":"$credentialId","aaguid":"00000000-0000-0000-0000-000000000000","registrationPlatform":"android","deviceType":"multiDevice","backedUp":true,"transports":["internal","internal"]}],"rpId":"fearlesswallet.io","schemaVersion":1}""",
            """{"storageKey":"wallet-1234","credentials":[$validSummary,$validSummary],"rpId":"fearlesswallet.io","schemaVersion":1}""",
            """{"storageKey":"wallet-1234","credentials":[$oversizedCredentialList],"rpId":"fearlesswallet.io","schemaVersion":1}"""
        )

        malformed.forEach { body ->
            val error = runCatching {
                runBlocking { service(transport = RecordingChallengeTransport(jsonResponse(body))).listCredentials("wallet-1234") }
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException || error is IllegalStateException)
        }

        val nonZeroRevokeAll = runCatching {
            runBlocking {
                service(
                    transport = RecordingChallengeTransport(
                        jsonResponse(
                            """{"storageKey":"wallet-1234","remainingCredentials":1,"rpId":"fearlesswallet.io","schemaVersion":1}"""
                        )
                    )
                ).revokeAllCredentials("wallet-1234")
            }
        }.exceptionOrNull()
        assertTrue(nonZeroRevokeAll is IllegalArgumentException)
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
    fun `challenge service rejects credentials in base URL`() {
        service(baseUrl = "https://operator:secret@backup.fearlesswallet.io")
    }

    @Test
    fun `challenge service rejects noncanonical base URL aliases`() {
        val rejected = listOf(
            " https://backup.fearlesswallet.io",
            "https://backup.fearlesswallet.io ",
            "https://backup.fearlesswallet.io:443",
            "https://backup.fearlesswallet.io/api",
            "https://backup.fearlesswallet.io//",
            "https://BACKUP.fearlesswallet.io"
        )

        rejected.forEach { baseUrl ->
            val error = runCatching { service(baseUrl = baseUrl) }.exceptionOrNull()
            assertTrue("Expected base URL alias to be rejected: $baseUrl", error is IllegalArgumentException)
        }
    }

    @Test
    fun `all ceremony posts authorize the exact transmitted body`() = runBlocking {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(32) { (it + 1).toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """{"registrationId":"registration-1234","challenge":"${base64Url(challenge)}","userId":"${base64Url(userId)}","userName":"alice@example.com","displayName":"Alice","storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1}"""
            ),
            jsonResponse("""{"storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1}"""),
            jsonResponse(
                """{"assertionId":"assertion-1234","challenge":"${base64Url(challenge)}","storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1}"""
            ),
            jsonResponse("""{"storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1}""")
        )
        val authorization = RecordingAuthorizationProvider()
        val service = service(transport = transport, authorizationProvider = authorization)

        service.registrationChallenge("wallet-001", "alice@example.com", "Alice")
        service.completeRegistration("registration-1234", """{"id":"credential"}""")
        service.assertionChallenge("wallet-1234")
        service.completeAssertion("assertion-1234", """{"id":"credential"}""")

        assertEquals(
            listOf(
                "/api/passkey-backup/v1/registration/challenge",
                "/api/passkey-backup/v1/registration/complete",
                "/api/passkey-backup/v1/assertion/challenge",
                "/api/passkey-backup/v1/assertion/complete"
            ),
            authorization.requests.map { it.path }
        )
        authorization.requests.zip(transport.requests).forEach { (authorizationRequest, request) ->
            assertEquals("POST", authorizationRequest.method)
            assertEquals(sha256Base64Url(requireNotNull(request.body)), authorizationRequest.bodySha256)
            assertEquals("Bearer test.authorization-token_123", request.headers["Authorization"])
        }
    }

    @Test
    fun `authorization request accepts only the exact seven service paths`() {
        val digest = base64Url(ByteArray(32))
        val allowedPaths = listOf(
            PasskeyBackupAuthorizationRequest.REGISTRATION_CHALLENGE_PATH,
            PasskeyBackupAuthorizationRequest.REGISTRATION_COMPLETE_PATH,
            PasskeyBackupAuthorizationRequest.ASSERTION_CHALLENGE_PATH,
            PasskeyBackupAuthorizationRequest.ASSERTION_COMPLETE_PATH,
            PasskeyBackupAuthorizationRequest.CREDENTIALS_LIST_PATH,
            PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH,
            PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_ALL_PATH
        )

        allowedPaths.forEach { path ->
            PasskeyBackupAuthorizationRequest(method = "POST", path = path, bodySha256 = digest)
        }

        val rejectedPaths = listOf(
            "/api/passkey-backup/v1/registration/challenge/extra",
            "/api/passkey-backup/v1/registration/../assertion/challenge",
            "/api/passkey-backup/v1/%2e%2e/admin",
            "/api/passkey-backup/v1/admin",
            "/api/passkey-backup/v1/assertion/challenge?scope=admin",
            "/api/passkey-backup/v1/credentials/revoke-all?admin=true",
            "/api/passkey-backup/v1/credentials/revoke-all/",
            "/api/passkey-backup/v1/credentials/revoke-all%2F..%2Flist",
            "//api/passkey-backup/v1/assertion/challenge",
            "https://backup.fearlesswallet.io/api/passkey-backup/v1/assertion/challenge",
            ""
        )

        rejectedPaths.forEach { path ->
            val error = runCatching {
                PasskeyBackupAuthorizationRequest(method = "POST", path = path, bodySha256 = digest)
            }.exceptionOrNull()
            assertTrue("Expected authorization path to be rejected: $path", error is IllegalArgumentException)
        }
    }

    @Test
    fun `authorization request rejects noncanonical body digests`() {
        val path = PasskeyBackupAuthorizationRequest.ASSERTION_CHALLENGE_PATH
        val canonicalDigest = base64Url(ByteArray(32))
        val rejectedDigests = listOf(
            canonicalDigest.dropLast(1),
            "$canonicalDigest=",
            canonicalDigest.dropLast(1) + "B",
            canonicalDigest.dropLast(1) + "+",
            " $canonicalDigest",
            ""
        )

        rejectedDigests.forEach { digest ->
            val error = runCatching {
                PasskeyBackupAuthorizationRequest(method = "POST", path = path, bodySha256 = digest)
            }.exceptionOrNull()
            assertTrue("Expected authorization digest to be rejected: $digest", error is IllegalArgumentException)
        }
    }

    @Test
    fun `challenge service fails closed when authorization provider is unavailable`() {
        val transport = RecordingChallengeTransport()
        val service = HttpPasskeyBackupChallengeService(
            baseUrl = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
            transport = transport
        )

        val error = runCatching { runBlocking { service.assertionChallenge("wallet-1234") } }.exceptionOrNull()

        assertTrue(error is UnsupportedOperationException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `challenge service rejects malformed bearer tokens before transport`() {
        val malformedTokens = listOf(
            "",
            " token",
            "token ",
            "two tokens",
            "token\nvalue",
            "token,value",
            "Bearer token",
            "abc=def",
            "a".repeat(4097)
        )

        malformedTokens.forEach { token ->
            val transport = RecordingChallengeTransport()
            val service = service(
                transport = transport,
                authorizationProvider = RecordingAuthorizationProvider(token)
            )

            val error = runCatching {
                runBlocking { service.assertionChallenge("wallet-1234") }
            }.exceptionOrNull()

            assertTrue("Expected token to be rejected: ${token.take(32)}", error is IllegalArgumentException)
            assertTrue(transport.requests.isEmpty())
        }
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

    @Test
    fun `challenge service rejects multiple-at account before authorization or network`() {
        val transport = RecordingChallengeTransport()
        val authorization = RecordingAuthorizationProvider()
        val service = service(transport = transport, authorizationProvider = authorization)

        val error = runCatching {
            runBlocking {
                service.registrationChallenge(
                    walletId = "wallet-001",
                    accountName = "alice@@example.com",
                    displayName = "Alice"
                )
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(authorization.requests.isEmpty())
        assertTrue(transport.requests.isEmpty())
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
        val userId = ByteArray(32) { it.toByte() }
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
        val userId = ByteArray(32) { it.toByte() }
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
    fun `registration challenge rejects fractional schema version`() {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(32) { it.toByte() }
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
                  "schemaVersion": 1.5
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
    fun `registration challenge rejects padded base64url`() {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(32) { it.toByte() }
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """
                {
                  "registrationId": "registration-1234",
                  "challenge": "${base64Url(challenge)}=",
                  "userId": "${base64Url(userId)}",
                  "userName": "alice@example.com",
                  "displayName": "Alice",
                  "storageKey": "wallet-1234",
                  "rpId": "${PasskeyBackupContract.PASSKEY_RP_ID}",
                  "schemaVersion": 1
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
        val userId = ByteArray(32) { it.toByte() }
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
        val userId = ByteArray(31) { it.toByte() }
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
    fun `challenge service rejects non-200 success statuses`() {
        listOf(201, 204).forEach { status ->
            val transport = RecordingChallengeTransport(
                GoogleDriveHttpResponse(
                    code = status,
                    body = """{"storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1}"""
                        .toByteArray()
                )
            )
            val error = runCatching {
                runBlocking { service(transport = transport).completeAssertion("assertion-1234", """{"id":"cred"}""") }
            }.exceptionOrNull()
            assertTrue("Expected HTTP $status to be rejected", error is IllegalArgumentException)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `challenge service rejects unknown response fields`() {
        val transport = RecordingChallengeTransport(
            jsonResponse(
                """{"storageKey":"wallet-1234","rpId":"fearlesswallet.io","schemaVersion":1,"unexpected":true}"""
            )
        )

        runBlocking {
            service(transport = transport).completeAssertion("assertion-1234", """{"id":"cred"}""")
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
        transport: GoogleDriveHttpTransport = RecordingChallengeTransport(),
        authorizationProvider: PasskeyBackupAuthorizationProvider = RecordingAuthorizationProvider()
    ): HttpPasskeyBackupChallengeService {
        return HttpPasskeyBackupChallengeService(
            baseUrl = baseUrl,
            transport = transport,
            authorizationProvider = authorizationProvider
        )
    }

    private fun jsonResponse(body: String): GoogleDriveHttpResponse {
        return GoogleDriveHttpResponse(code = 200, body = body.toByteArray())
    }

    private fun base64Url(value: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }

    private fun sha256Base64Url(value: ByteArray): String {
        return base64Url(MessageDigest.getInstance("SHA-256").digest(value))
    }

    private fun GoogleDriveHttpRequest.jsonBody(): JsonObject {
        return JsonParser.parseString(bodyText()).asJsonObject
    }

    private fun GoogleDriveHttpRequest.bodyText(): String {
        return body?.toString(Charsets.UTF_8).orEmpty()
    }

    private class CommitThenResetServer : AutoCloseable {
        private val serverSocket = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = SECOND_ACCEPT_TIMEOUT_MILLIS
        }
        private val committed = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        private lateinit var worker: Thread

        val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        val acceptCount = AtomicInteger()
        val requestLines = CopyOnWriteArrayList<String>()

        fun start() {
            worker = thread(name = "passkey-registration-reset-server") {
                try {
                    val first = serverSocket.accept()
                    acceptCount.incrementAndGet()
                    requestLines += readCompleteRequest(first)
                    committed.countDown()
                    first.setSoLinger(true, 0)
                    first.close()

                    try {
                        serverSocket.accept().use { replay ->
                            acceptCount.incrementAndGet()
                            requestLines += readCompleteRequest(replay)
                            replay.getOutputStream().run {
                                write(NOT_FOUND_RESPONSE)
                                flush()
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Expected: a one-shot request body forbids the post-dispatch replay.
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                } finally {
                    serverSocket.close()
                }
            }
        }

        fun awaitFinished() {
            check(committed.await(SERVER_COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Registration completion body was not fully received by the reset server"
            }
            worker.join(TimeUnit.SECONDS.toMillis(SERVER_COMPLETION_TIMEOUT_SECONDS))
            check(!worker.isAlive) { "Reset server did not finish within the test timeout" }
            failure.get()?.let { throw AssertionError("Reset server failed", it) }
        }

        override fun close() {
            serverSocket.close()
            if (this::worker.isInitialized) {
                worker.join(TimeUnit.SECONDS.toMillis(SERVER_COMPLETION_TIMEOUT_SECONDS))
            }
        }

        private fun readCompleteRequest(socket: Socket): String {
            socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
            val input = socket.getInputStream()
            val headerBytes = ByteArrayOutputStream()
            var matchedTerminatorBytes = 0
            while (matchedTerminatorBytes < HEADER_TERMINATOR.size) {
                val value = input.read()
                check(value >= 0) { "Socket closed before HTTP headers completed" }
                headerBytes.write(value)
                check(headerBytes.size() <= MAX_HEADER_BYTES) { "HTTP request headers exceeded test limit" }
                matchedTerminatorBytes = when {
                    value.toByte() == HEADER_TERMINATOR[matchedTerminatorBytes] -> matchedTerminatorBytes + 1
                    value.toByte() == HEADER_TERMINATOR[0] -> 1
                    else -> 0
                }
            }

            val headerText = headerBytes.toString(Charsets.US_ASCII.name())
            val lines = headerText.split("\r\n")
            val contentLength = lines.firstOrNull {
                it.startsWith("Content-Length:", ignoreCase = true)
            }?.substringAfter(':')?.trim()?.toIntOrNull()
            check(contentLength != null && contentLength >= 0) { "HTTP request omitted Content-Length" }

            var remaining = contentLength
            val buffer = ByteArray(DEFAULT_BODY_BUFFER_BYTES)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                check(read > 0) { "Socket closed before HTTP body completed" }
                remaining -= read
            }
            return lines.first()
        }

        private companion object {
            const val SECOND_ACCEPT_TIMEOUT_MILLIS = 1_000
            const val SOCKET_READ_TIMEOUT_MILLIS = 2_000
            const val SERVER_COMPLETION_TIMEOUT_SECONDS = 3L
            const val MAX_HEADER_BYTES = 64 * 1024
            const val DEFAULT_BODY_BUFFER_BYTES = 4 * 1024
            val HEADER_TERMINATOR = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
            val NOT_FOUND_RESPONSE = (
                "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)
        }
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

    private class RecordingAuthorizationProvider(
        private val token: String = "test.authorization-token_123"
    ) : PasskeyBackupAuthorizationProvider {
        val requests = mutableListOf<PasskeyBackupAuthorizationRequest>()

        override suspend fun authorizationToken(request: PasskeyBackupAuthorizationRequest): String {
            requests += request
            return token
        }
    }
}
