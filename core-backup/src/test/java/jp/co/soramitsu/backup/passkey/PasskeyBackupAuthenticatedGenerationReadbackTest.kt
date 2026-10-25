package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupAuthenticatedGenerationReadbackTest {
    @Test
    fun `verified directed assertion decrypts exact head and rechecks owner and Drive account`() = runBlocking {
        val fixture = Fixture()
        fixture.authority.onCompletion = {
            assertTrue(requireNotNull(fixture.ceremony.lastResult).hasLocalPrfOutput)
        }

        val proof = fixture.readback().verify(fixture.storageKey, fixture.credentialId)

        assertEquals(fixture.context.generationId, proof.generationId)
        assertEquals(GenerationFixture.digest, proof.bundleSha256)
        assertEquals(fixture.wallet.publicIdentitySha256, proof.publicIdentitySha256)
        assertEquals(2, fixture.authority.headReads)
        assertEquals(1, fixture.authority.completions)
        assertEquals(1, fixture.walletVerifications)
        assertFalse(requireNotNull(fixture.ceremony.lastResult).hasLocalPrfOutput)
        assertEquals(4, fixture.accountAccesses)
        assertEquals(listOf("GET", "GET"), fixture.requests.map { it.method })
        assertTrue(fixture.requests.all { it.url.contains("/files/allocated-file?") })
        val options = JsonParser.parseString(fixture.ceremony.requestJson).asJsonObject
        val allowed = options.getAsJsonArray("allowCredentials")
        assertEquals(1, allowed.size())
        assertEquals(fixture.credentialId, allowed.first().asJsonObject.get("id").asString)
        val expectedSalt = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(GenerationFixture.generation().wrappers.single().prfSalt)
        assertEquals(
            expectedSalt,
            options.getAsJsonObject("extensions").getAsJsonObject("prf")
                .getAsJsonObject("eval").get("first").asString
        )
        assertFalse(fixture.authority.serverCredentialJson.contains("prf"))
        assertFalse(fixture.authority.serverCredentialJson.contains(fixture.prfEncoded))
        assertFalse(proof.toString().contains(fixture.wallet.walletId))
        assertFalse(fixture.snapshot.toString().contains(fixture.credentialId))
        assertFalse(fixture.authority.lastVerified.toString().contains(fixture.credentialId))
    }

    @Test
    fun `server rejection occurs before local PRF is consumed or wallet material is decrypted`() = runBlocking {
        val fixture = Fixture()
        fixture.authority.onCompletion = {
            assertTrue(requireNotNull(fixture.ceremony.lastResult).hasLocalPrfOutput)
            error("Server rejected the signed assertion")
        }

        fails { fixture.readback().verify(fixture.storageKey, fixture.credentialId) }

        assertEquals(1, fixture.authority.completions)
        assertEquals(0, fixture.walletVerifications)
        assertFalse(requireNotNull(fixture.ceremony.lastResult).hasLocalPrfOutput)
    }

    @Test
    fun `compiled release disable blocks readback before owner or Drive access`() = runBlocking {
        val fixture = Fixture()
        fails { fixture.readback(enabled = false).verify(fixture.storageKey, fixture.credentialId) }
        assertEquals(0, fixture.authority.headReads)
        assertEquals(0, fixture.accountAccesses)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `missing PRF or substituted credential cannot reach owner completion or wallet verifier`() = runBlocking {
        for (substitution in listOf(false, true)) {
            val fixture = Fixture()
            if (substitution) {
                fixture.ceremony.responseCredentialId = "AQ"
            } else {
                fixture.ceremony.includePrf = false
            }
            fails { fixture.readback().verify(fixture.storageKey, fixture.credentialId) }
            assertEquals(0, fixture.authority.completions)
            assertEquals(0, fixture.walletVerifications)
            assertEquals(1, fixture.authority.headReads)
        }
    }

    @Test
    fun `tampered Drive bytes fail before challenge or native ceremony`() = runBlocking {
        val fixture = Fixture()
        fixture.media = GenerationFixture.bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        fails { fixture.readback().verify(fixture.storageKey, fixture.credentialId) }
        assertEquals(0, fixture.authority.challenges)
        assertEquals(0, fixture.ceremony.calls)
        assertEquals(0, fixture.walletVerifications)
    }

    @Test
    fun `wrong owner scope and challenge echo fail before PRF ceremony`() = runBlocking {
        val wrongOwner = Fixture()
        wrongOwner.authority.initialSnapshot = wrongOwner.snapshotWithWallet("other-wallet")
        fails { wrongOwner.readback().verify(wrongOwner.storageKey, wrongOwner.credentialId) }
        assertTrue(wrongOwner.requests.isEmpty())

        val wrongChallenge = Fixture()
        wrongChallenge.authority.challengeCredentialId = "AQ"
        fails { wrongChallenge.readback().verify(wrongChallenge.storageKey, wrongChallenge.credentialId) }
        assertEquals(0, wrongChallenge.ceremony.calls)
        assertEquals(0, wrongChallenge.authority.completions)
    }

    @Test
    fun `changed signed head or wallet identity cannot yield local readback evidence`() = runBlocking {
        val atAssertion = Fixture()
        atAssertion.authority.completionSnapshot = atAssertion.snapshotWithDigest("c".repeat(64))
        fails { atAssertion.readback().verify(atAssertion.storageKey, atAssertion.credentialId) }
        assertEquals(0, atAssertion.walletVerifications)

        val afterDecryption = Fixture()
        afterDecryption.authority.latestSnapshot = afterDecryption.snapshotWithDigest("c".repeat(64))
        fails { afterDecryption.readback().verify(afterDecryption.storageKey, afterDecryption.credentialId) }
        assertEquals(1, afterDecryption.walletVerifications)

        val wrongWallet = Fixture()
        wrongWallet.authority.completionSnapshot = wrongWallet.snapshotWithWallet("other-wallet")
        fails { wrongWallet.readback().verify(wrongWallet.storageKey, wrongWallet.credentialId) }
        assertEquals(0, wrongWallet.walletVerifications)
    }

    @Test
    fun `selected Google subject change after decryption cannot yield evidence`() = runBlocking {
        val fixture = Fixture()
        fixture.changedSubjectAt = 3
        fails { fixture.readback().verify(fixture.storageKey, fixture.credentialId) }
        assertEquals(1, fixture.walletVerifications)
        assertEquals(1, fixture.authority.headReads)
    }

    private suspend fun fails(block: suspend () -> Any?) = assertTrue(runCatching { block() }.isFailure)

    private class Fixture {
        val context = GenerationFixture.context
        val storageKey = "wallet-1234"
        val credentialId = GenerationFixture.json["credentialId"].asString
        val wallet = PasskeyBackupExpectedWalletIdentity(storageKey, "wallet-001", "b".repeat(64))
        val snapshot = snapshotWithDigest(GenerationFixture.digest)
        val prfEncoded: String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { 0x66 })
        val requests = mutableListOf<GoogleDriveHttpRequest>()
        var media: ByteArray = GenerationFixture.bytes
        var accountAccesses = 0
        var changedSubjectAt = Int.MAX_VALUE
        var walletVerifications = 0
        val authority = RecordingAuthority(snapshot, credentialId)
        val ceremony = RecordingCeremony(credentialId, prfEncoded)
        private val storage = GoogleDrivePasskeyBackupGenerationStorage(
            "google-subject-123",
            GoogleDriveAccessTokenProvider {
                accountAccesses++
                val subject = if (accountAccesses == changedSubjectAt) "wrong-subject" else "google-subject-123"
                GoogleDriveAccountAccess(subject, "selected@example.com", "synthetic-token")
            },
            object : GoogleDriveHttpTransport {
                override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                    requests += request
                    return if (request.url.endsWith("?alt=media")) {
                        GoogleDriveHttpResponse(200, media)
                    } else {
                        GoogleDriveHttpResponse(200, metadata().toString().toByteArray())
                    }
                }
            }
        )

        fun readback(enabled: Boolean = true) = PasskeyBackupAuthenticatedGenerationReadback(
            authority, storage, ceremony,
            PasskeyBackupGenerationCryptographicVerifier(
                PasskeyBackupPlaintextWalletVerifier { plaintext, expected ->
                    walletVerifications++
                    assertEquals("cross-platform-passkey-backup", plaintext.toString(Charsets.UTF_8))
                    PasskeyBackupLocalWalletEvidence(
                        expected.storageKey, expected.walletId, expected.publicIdentitySha256,
                        decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
                    )
                }
            ),
            isReleaseEnabled = enabled
        )

        fun snapshotWithWallet(storageKey: String) = PasskeyBackupRecoveryHeadSnapshot(
            credentialId,
            PasskeyBackupExpectedWalletIdentity(storageKey, wallet.walletId, wallet.publicIdentitySha256),
            head(GenerationFixture.digest)
        )

        fun snapshotWithDigest(digest: String) = PasskeyBackupRecoveryHeadSnapshot(
            credentialId, wallet, head(digest)
        )

        private fun head(digest: String): PasskeyBackupAuthenticatedHead {
            val previous = PasskeyBackupHeadDescriptor(
                6, 5, "b".repeat(64), "E".repeat(43), "a".repeat(64),
                context.keyEpoch, "previous-drive-id", context.storageAccountBinding
            )
            val current = PasskeyBackupHeadDescriptor(
                7, 6, context.parentHeadSha256, context.generationId, digest,
                context.keyEpoch, "allocated-file", context.storageAccountBinding
            )
            return PasskeyBackupAuthenticatedHead(
                context.ownerSubject, context.backupNamespace, current, previous,
                context.ownerSubject, context.backupNamespace, context.storageAccountBinding
            )
        }

        private fun metadata() = JsonObject().apply {
            addProperty("id", "allocated-file")
            addProperty("name", "fearless-passkey-generation-${context.generationId}.bin")
            addProperty("mimeType", "application/octet-stream")
            addProperty("size", GenerationFixture.bytes.size.toString())
            add("spaces", JsonArray().apply { add("appDataFolder") })
            add(
                "appProperties",
                JsonObject().apply {
                    addProperty("format", "FPBKGEN1")
                    addProperty("namespaceSha256", PasskeyBackupGenerationFormat.sha256(context.backupNamespace.toByteArray()))
                    addProperty("generationId", context.generationId)
                    addProperty("bundleSha256", GenerationFixture.digest)
                }
            )
        }
    }

    private class RecordingAuthority(
        var initialSnapshot: PasskeyBackupRecoveryHeadSnapshot,
        private val credentialId: String
    ) : PasskeyBackupRecoveryAuthority {
        var headReads = 0
        var challenges = 0
        var completions = 0
        var challengeCredentialId = credentialId
        var completionSnapshot: PasskeyBackupRecoveryHeadSnapshot? = null
        var latestSnapshot: PasskeyBackupRecoveryHeadSnapshot? = null
        var serverCredentialJson = ""
        var onCompletion: (() -> Unit)? = null
        lateinit var lastVerified: PasskeyBackupVerifiedAssertionHead

        override suspend fun readAuthorizedHead(
            storageKey: String,
            credentialId: String
        ): PasskeyBackupRecoveryHeadSnapshot {
            headReads++
            return if (headReads == 1) initialSnapshot else latestSnapshot ?: initialSnapshot
        }

        override suspend fun assertionChallenge(
            storageKey: String,
            credentialId: String
        ): PasskeyBackupAssertionChallenge {
            challenges++
            return PasskeyBackupAssertionChallenge(
                "assertion-1234", ByteArray(32) { it.toByte() }, storageKey,
                credentialId = challengeCredentialId
            )
        }

        override suspend fun verifyAssertionAndReadHead(
            assertionId: String,
            serverCredentialJson: String
        ): PasskeyBackupVerifiedAssertionHead {
            completions++
            this.serverCredentialJson = serverCredentialJson
            onCompletion?.invoke()
            return PasskeyBackupVerifiedAssertionHead(
                assertionId, credentialId, completionSnapshot ?: initialSnapshot
            ).also { lastVerified = it }
        }
    }

    private class RecordingCeremony(
        private val credentialId: String,
        private val prfEncoded: String
    ) : PasskeyBackupCeremonyExecutor {
        var calls = 0
        var includePrf = true
        var responseCredentialId = credentialId
        var requestJson = ""
        var lastResult: PasskeyBackupNativeCeremonyResult? = null

        override suspend fun performRegistration(
            pending: PendingPasskeyBackupRegistration
        ): PasskeyBackupNativeCeremonyResult {
            error("Registration is not part of readback")
        }

        override suspend fun performAssertion(
            pending: PendingPasskeyBackupAssertion
        ): PasskeyBackupNativeCeremonyResult {
            calls++
            requestJson = pending.requestJson
            val extension = if (includePrf) {
                "{\"prf\":{\"results\":{\"first\":\"$prfEncoded\"}}}"
            } else {
                "{}"
            }
            return PasskeyBackupNativeCeremonyResult.assertion(
                """{"id":"$responseCredentialId","rawId":"$responseCredentialId","type":"public-key","response":{"clientDataJSON":"AQ","authenticatorData":"AQ","signature":"AQ","userHandle":null},"clientExtensionResults":$extension}""",
                pending.requestJson
            ).also { lastResult = it }
        }
    }
}
