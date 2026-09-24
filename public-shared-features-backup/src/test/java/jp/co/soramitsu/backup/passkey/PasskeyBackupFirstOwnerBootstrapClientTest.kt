package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupFirstOwnerBootstrapClientTest {
    private val vectors by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/bootstrap-play-integrity-vectors.json"))
        stream.use { JsonParser.parseReader(it.reader()).asJsonObject }
    }
    private val wallet = PasskeyBackupExpectedWalletIdentity("wallet-storage-1", "wallet-id-1", "a".repeat(64))
    private val nowSeconds = 1_759_999_900L

    @Test
    fun `signed message matches independent server vector`() {
        val ceremony = vectors.getAsJsonObject("ceremony")
        val challenge = PasskeyBackupFirstOwnerChallenge(
            ceremony.get("ceremonyId").asString,
            decode(ceremony.get("challenge").asString),
            ceremony.get("subject").asString,
            ceremony.get("namespace").asString,
            decode(ceremony.get("userHandle").asString),
            ceremony.get("expiresAt").asLong
        )

        val message = PasskeyBackupFirstOwnerProof.message(challenge, vectors.get("credential").toString())

        assertEquals(vectors.get("walletMessage").asString, encode(message))
        assertEquals(
            proofVector().get("requestHash").asString,
            PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
                message, PasskeyBackupBootstrapWalletProofBinding.Scheme.ED25519,
                decode(proofVector().get("publicKey").asString),
                decode(proofVector().get("signature").asString)
            ).requestHash
        )
    }

    @Test
    fun `verified first owner has empty authenticated head and sends no PRF or wallet secret`() = runBlocking {
        val fixture = Fixture()

        val result = fixture.client().bootstrap(wallet, ACCOUNT, "First wallet")

        assertEquals(fixture.ceremony.get("subject").asString, result.session.subject)
        assertEquals(fixture.ceremony.get("namespace").asString, result.session.namespace)
        assertEquals(fixture.credential.get("id").asString, result.credentialId)
        assertTrue(result.emptyHead.head == null)
        assertTrue(result.emptyHead.previous == null)
        assertFalse(result.toString().contains(result.session.sessionToken))
        assertEquals(3, fixture.transport.requests.size)
        assertEquals(1, fixture.authorizer.authorizations)
        assertEquals(1, fixture.authorizer.signatures)
        assertEquals(1, fixture.integrityGateway.requests.size)
        assertEquals(proofVector().get("requestHash").asString, fixture.integrityGateway.requests.single())

        val registration = JsonParser.parseString(requireNotNull(fixture.ceremonyExecutor.requestJson)).asJsonObject
        assertEquals("required", registration.getAsJsonObject("authenticatorSelection").get("residentKey").asString)
        assertEquals("required", registration.getAsJsonObject("authenticatorSelection").get("userVerification").asString)
        assertTrue(registration.getAsJsonObject("extensions").has("prf"))

        val completion = fixture.transport.requests[1]
        assertTrue(completion.isOneShot)
        val body = JsonParser.parseString(requireNotNull(completion.body).toString(Charsets.UTF_8)).asJsonObject
        assertEquals(
            setOf("schemaVersion", "ceremonyId", "credential", "walletProof", "appAttestation"),
            body.keySet()
        )
        assertEquals(setOf("kind", "token"), body.getAsJsonObject("appAttestation").keySet())
        assertEquals(fixture.credential, body.getAsJsonObject("credential"))
        assertFalse(completion.bodyText().contains("prf"))
        assertFalse(completion.bodyText().contains("local-secret"))
        assertFalse(completion.bodyText().contains(encode(ByteArray(32) { 42 })))
        assertEquals("Bearer ${result.session.sessionToken}", fixture.transport.requests[2].headers["Authorization"])
    }

    @Test
    fun `release flag and failed original wallet evidence stop before network`() = runBlocking {
        val disabled = Fixture()
        assertTrue(runCatching { disabled.client(enabled = false).bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(0, disabled.authorizer.authorizations)
        assertTrue(disabled.transport.requests.isEmpty())

        val rejected = Fixture().apply { authorizer.valid = false }
        assertTrue(runCatching { rejected.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(1, rejected.authorizer.authorizations)
        assertTrue(rejected.transport.requests.isEmpty())
    }

    @Test
    fun `signer for another original wallet fails before attestation or owner creation`() = runBlocking {
        listOf(
            Fixture().apply { authorizer.wrongSigningWallet = true },
            Fixture().apply { authorizer.wrongSigningKey = true }
        ).forEach { fixture ->
            assertTrue(runCatching { fixture.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
            assertEquals(1, fixture.authorizer.signatures)
            assertEquals(0, fixture.integrityGateway.requests.size)
            assertEquals(1, fixture.transport.requests.size)
        }
    }

    @Test
    fun `invalid challenge and missing native PRF never request attestation or create owner`() = runBlocking {
        val malformed = Fixture().apply {
            challengeOverride = challengeBody().replace("\"kind\":\"bootstrap\"", "\"kind\":\"authentication\"")
        }
        assertTrue(runCatching { malformed.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(0, malformed.ceremonyExecutor.calls)
        assertEquals(1, malformed.transport.requests.size)

        val noPrf = Fixture().apply { ceremonyExecutor.includePrf = false }
        assertTrue(runCatching { noPrf.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(1, noPrf.ceremonyExecutor.calls)
        assertEquals(0, noPrf.authorizer.signatures)
        assertEquals(0, noPrf.integrityGateway.requests.size)
        assertEquals(1, noPrf.transport.requests.size)
    }

    @Test
    fun `changed selected Google account and nonempty owner head fail closed`() = runBlocking {
        val switched = Fixture().apply { accountProvider.switchAfter = 1 }
        assertTrue(runCatching { switched.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(1, switched.transport.requests.size)
        assertEquals(0, switched.integrityGateway.requests.size)

        val occupied = Fixture().apply { nonemptyHead = true }
        assertTrue(runCatching { occupied.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(3, occupied.transport.requests.size)
    }

    @Test
    fun `duplicate or substituted session response is rejected after one shot completion`() = runBlocking {
        val duplicate = Fixture().apply {
            sessionOverride = sessionBody().dropLast(1) + ",\"generation\":0}"
        }
        assertTrue(runCatching { duplicate.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(2, duplicate.transport.requests.size)

        val substituted = Fixture().apply {
            sessionOverride = sessionBody().replace(ceremony.get("subject").asString, "owner:${"Z".repeat(43)}")
        }
        assertTrue(runCatching { substituted.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)
        assertEquals(2, substituted.transport.requests.size)
    }

    @Test
    fun `unknown or cancelled completion sends no second owner creation request`() = runBlocking {
        listOf(IllegalStateException("network outcome unknown"), CancellationException("cancelled")).forEach { failure ->
            val fixture = Fixture().apply { completeFailure = failure }

            assertTrue(runCatching { fixture.client().bootstrap(wallet, ACCOUNT, "Wallet") }.isFailure)

            assertEquals(2, fixture.transport.requests.size)
            assertEquals(1, fixture.transport.requests.count { it.url.endsWith("/owner/bootstrap/complete") })
            assertTrue(fixture.transport.requests.none { it.url.endsWith("/owner/backup/head") })
        }
    }

    private inner class Fixture {
        val ceremony = vectors.getAsJsonObject("ceremony")
        val credential = vectors.getAsJsonObject("credential")
        val accountProvider = RecordingAccountProvider()
        val authorizer = RecordingAuthorizer()
        val ceremonyExecutor = RecordingCeremonyExecutor()
        val integrityGateway = RecordingIntegrityGateway()
        val transport = RecordingTransport(this)
        var challengeOverride: String? = null
        var sessionOverride: String? = null
        var nonemptyHead = false
        var completeFailure: Exception? = null

        fun client(enabled: Boolean = true): PasskeyBackupFirstOwnerBootstrapClient {
            val head = PasskeyBackupOwnerHeadHttpClient(
                accountProvider, transport, BASE_URL, { nowSeconds * 1_000L }, enabled
            )
            val integrity = PasskeyBackupPlayIntegrityBootstrapRequester(
                PasskeyBackupPlayIntegrityCloudProject(123_456_789L), integrityGateway, enabled
            )
            return PasskeyBackupFirstOwnerBootstrapClient(
                authorizer, accountProvider, ceremonyExecutor, integrity, head, transport,
                BASE_URL, nowMillis = { nowSeconds * 1_000L }, isReleaseEnabled = enabled
            )
        }

        fun challengeBody(): String = ceremony.deepCopy().apply {
            addProperty("expiresAt", nowSeconds + 100L)
        }.toString()

        fun sessionBody(): String = JsonObject().apply {
            addProperty("sessionToken", "session.${encode(ByteArray(32) { 9 })}")
            addProperty("subject", ceremony.get("subject").asString)
            addProperty("namespace", ceremony.get("namespace").asString)
            addProperty("generation", 0)
            addProperty("platform", "android")
            addProperty("expiresAt", nowSeconds + 600L)
        }.toString()

        fun headBody(): String = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("ownerSubject", ceremony.get("subject").asString)
            addProperty("backupNamespace", ceremony.get("namespace").asString)
            if (nonemptyHead) {
                val binding = PasskeyBackupGenerationFormat.storageAccountBinding("google-subject-1")
                add(
                    "head",
                    JsonObject().apply {
                        addProperty("headRevision", "1")
                        addProperty("parentHeadRevision", "0")
                        add("parentHeadSha256", com.google.gson.JsonNull.INSTANCE)
                        addProperty("generationId", encode(ByteArray(32) { 10 }))
                        addProperty("bundleSha256", "a".repeat(64))
                        addProperty("keyEpoch", "1")
                        addProperty("driveFileId", "drive-first-owner")
                        addProperty("storageAccountBinding", binding)
                    }
                )
            } else {
                add("head", com.google.gson.JsonNull.INSTANCE)
            }
            add("previous", com.google.gson.JsonNull.INSTANCE)
        }.toString()
    }

    private inner class RecordingAccountProvider : GoogleDriveAccessTokenProvider {
        var calls = 0
        var switchAfter = Int.MAX_VALUE
        override suspend fun accessToken(): GoogleDriveAccountAccess {
            calls++
            return GoogleDriveAccountAccess(
                if (calls > switchAfter) {
                    "other-google-subject"
                } else {
                    "google-subject-1"
                },
                ACCOUNT,
                "token-value"
            )
        }
    }

    private inner class RecordingAuthorizer : PasskeyBackupFirstOwnerWalletAuthorizer {
        var authorizations = 0
        var signatures = 0
        var valid = true
        var wrongSigningWallet = false
        var wrongSigningKey = false

        override suspend fun authorizeAndVerifyOriginalKeys(
            expectedWallet: PasskeyBackupExpectedWalletIdentity
        ): PasskeyBackupFirstOwnerAuthorizedWallet {
            authorizations++
            return PasskeyBackupFirstOwnerAuthorizedWallet(
                PasskeyBackupLocalWalletEvidence(
                    expectedWallet.storageKey, expectedWallet.walletId, expectedWallet.publicIdentitySha256,
                    valid, valid, valid
                ),
                PasskeyBackupBootstrapWalletProofBinding.Scheme.ED25519,
                decode(proofVector().get("publicKey").asString)
            )
        }

        override suspend fun signOriginalWalletMessage(
            message: ByteArray,
            expectedWallet: PasskeyBackupExpectedWalletIdentity
        ): PasskeyBackupFirstOwnerSignedProof {
            signatures++
            assertEquals(wallet.walletId, expectedWallet.walletId)
            assertEquals(vectors.get("walletMessage").asString, encode(message))
            return PasskeyBackupFirstOwnerSignedProof(
                PasskeyBackupBootstrapWalletProofBinding.Scheme.ED25519,
                if (wrongSigningKey) ByteArray(32) { 17 } else decode(proofVector().get("publicKey").asString),
                decode(proofVector().get("signature").asString),
                PasskeyBackupLocalWalletEvidence(
                    expectedWallet.storageKey,
                    if (wrongSigningWallet) "unrelated-wallet" else expectedWallet.walletId,
                    expectedWallet.publicIdentitySha256,
                    true,
                    true,
                    true
                )
            )
        }
    }

    private inner class RecordingCeremonyExecutor : PasskeyBackupCeremonyExecutor {
        var calls = 0
        var includePrf = true
        var requestJson: String? = null

        override suspend fun performRegistration(
            pending: PendingPasskeyBackupRegistration
        ): PasskeyBackupNativeCeremonyResult {
            calls++
            requestJson = pending.requestJson
            val raw = vectors.getAsJsonObject("credential").deepCopy()
            if (includePrf) {
                raw.add(
                    "clientExtensionResults",
                    JsonObject().apply {
                        add(
                            "prf",
                            JsonObject().apply {
                                addProperty("enabled", true)
                                add(
                                    "results",
                                    JsonObject().apply {
                                        addProperty("first", encode(ByteArray(32) { 42 }))
                                    }
                                )
                            }
                        )
                    }
                )
            }
            raw.addProperty("localSecret", "local-secret")
            return PasskeyBackupNativeCeremonyResult.registration(raw.toString())
        }

        override suspend fun performAssertion(
            pending: PendingPasskeyBackupAssertion
        ): PasskeyBackupNativeCeremonyResult = error("No assertion in first-owner bootstrap")
    }

    private class RecordingIntegrityGateway : PasskeyBackupStandardIntegrityGateway {
        val requests = mutableListOf<String>()
        override suspend fun prepare(cloudProjectNumber: Long): PasskeyBackupStandardIntegrityGateway.PreparedProvider =
            PasskeyBackupStandardIntegrityGateway.PreparedProvider { hash ->
                requests += hash
                "a".repeat(40)
            }
    }

    private class RecordingTransport(private val fixture: Fixture) : GoogleDriveHttpTransport {
        val requests = mutableListOf<GoogleDriveHttpRequest>()
        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            if (request.url.endsWith("/owner/bootstrap/complete")) {
                fixture.completeFailure?.let { throw it }
            }
            val body = when {
                request.url.endsWith("/owner/bootstrap/challenge") -> fixture.challengeOverride ?: fixture.challengeBody()
                request.url.endsWith("/owner/bootstrap/complete") -> fixture.sessionOverride ?: fixture.sessionBody()
                request.url.endsWith("/owner/backup/head") -> fixture.headBody()
                else -> error("Unexpected first-owner request")
            }
            return GoogleDriveHttpResponse(200, body.toByteArray(Charsets.UTF_8))
        }
    }

    private fun proofVector() = vectors.getAsJsonArray("proofs")
        .map { it.asJsonObject }.single { it.get("scheme").asString == "ed25519" }

    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
    private fun encode(value: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private companion object {
        const val BASE_URL = "https://backup.fearlesswallet.io"
        const val ACCOUNT = "owner@example.com"
    }
}

private fun GoogleDriveHttpRequest.bodyText(): String = requireNotNull(body).toString(Charsets.UTF_8)
