package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

class PasskeyBackupVerifiedGenerationPromotionTest {
    private val roots = mutableListOf<Path>()

    @After
    fun cleanup() { roots.forEach { it.toFile().deleteRecursively() } }

    @Test
    fun `exact Drive readback and original-key proof precede one admitted owner commit`() = runBlocking {
        val fixture = fixture()
        val result = fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)

        assertTrue(result is PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead)
        assertEquals(fixture.candidate.context.generationId, (result as PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead).generationId)
        assertEquals(fixture.candidate.sha256, result.bundleSha256)
        assertEquals(fixture.wallet.publicIdentitySha256, result.publicIdentitySha256)
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertTrue(fixture.events.indexOf("wallet-proof") < fixture.events.indexOf("grant"))
        assertTrue(fixture.events.indexOf("grant") < fixture.events.indexOf("commit"))
        assertTrue(fixture.events.indexOf("drive-media") < fixture.events.indexOf("wallet-proof"))
        assertFalse(fixture.driveRequests.any { it.method == "DELETE" || it.method == "PATCH" })
        assertEquals(fixture.oldHead.generationId, fixture.remotePrevious!!.generationId)
        assertFalse(result.toString().contains(fixture.wallet.walletId))
    }

    @Test
    fun `lost commit response and restart reconcile status and fresh head without another write`() = runBlocking {
        val fixture = fixture()
        fixture.loseCommitResponse = true
        val first = fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
        assertTrue(first is PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead)

        val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
        val second = fixture.coordinator(reopened).promote(fixture.session, fixture.operationId, fixture.wallet)
        assertTrue(second is PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead)
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertTrue(reopened.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
    }

    @Test
    fun `unknown commit with absent status stays pending after restart and never retries`() = runBlocking {
        val fixture = fixture()
        fixture.loseCommitResponse = true
        fixture.applyCommit = false
        assertEquals(
            PasskeyBackupGenerationPromotionResult.AwaitingOwnerOperation,
            fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
        )
        val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
        assertEquals(
            PasskeyBackupGenerationPromotionResult.AwaitingOwnerOperation,
            fixture.coordinator(reopened).promote(fixture.session, fixture.operationId, fixture.wallet)
        )
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.events.count { it == "wallet-proof" })
    }

    @Test
    fun `substituted operation descriptor cannot confirm head and status can recover later`() = runBlocking {
        val fixture = fixture()
        fixture.substituteStatus = true
        assertTrue(
            runCatching {
                fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
            }.isFailure
        )
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        fixture.substituteStatus = false
        val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
        assertTrue(
            fixture.coordinator(reopened).promote(fixture.session, fixture.operationId, fixture.wallet) is
                PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead
        )
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
    }

    @Test
    fun `session expiry during final Google recheck cannot report a verified head`() = runBlocking {
        val fixture = fixture()
        fixture.expireOnFinalAccountCheck = true
        assertTrue(
            runCatching {
                fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
            }.isFailure
        )
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(fixture.session.expiresAt * 1_000L, fixture.nowMillis)
    }

    @Test
    fun `post-CAS Drive disappearance denies current verified result without another CAS`() = runBlocking {
        val fixture = fixture()
        fixture.driveMissingAfterCommit = true
        assertTrue(
            runCatching {
                fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
            }.isFailure
        )
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        assertEquals(1, fixture.events.count { it == "wallet-proof" })
        fixture.driveMissingAfterCommit = false
        assertTrue(
            fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet) is
                PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead
        )
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
    }

    @Test
    fun `head advancing during committed Drive readback cannot return stale current proof`() = runBlocking {
        val fixture = fixture()
        fixture.advanceHeadDuringCommittedRead = true
        val failure = runCatching {
            fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
        }.exceptionOrNull()
        assertEquals("Authenticated backup head changed during promotion", failure?.message)
        assertEquals(3, fixture.headsAfterCommit)
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
    }

    @Test
    fun `head advancing during final account check cannot return stale current proof`() = runBlocking {
        val fixture = fixture()
        fixture.advanceHeadDuringFinalAccountCheck = true
        val failure = runCatching {
            fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
        }.exceptionOrNull()
        assertEquals("Authenticated backup head changed during promotion", failure?.message)
        assertEquals(3, fixture.headsAfterCommit)
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
    }

    @Test
    fun `torn commit marker still permits read-only status and local proof after restart`() = runBlocking {
        val fixture = fixture()
        fixture.loseCommitResponse = true
        assertTrue(
            fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet) is
                PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead
        )
        val marker = fixture.root.resolve(fixture.operationId + PasskeyBackupJournalDisk.COMMIT_SUFFIX)
        Files.write(marker, byteArrayOf(1))
        Files.setPosixFilePermissions(marker, PosixFilePermissions.fromString("rw-------"))
        val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
        assertTrue(runCatching { reopened.read(fixture.operationId, fixture.scope) }.isFailure)
        assertTrue(reopened.readForReconciliation(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        assertEquals(1, reopened.listForReconciliation(fixture.scope).size)
        assertTrue(
            fixture.coordinator(reopened).promote(fixture.session, fixture.operationId, fixture.wallet) is
                PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead
        )
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
        assertEquals(1, fixture.driveRequests.count { it.method == "POST" })
    }

    @Test
    fun `absent operation status that contradicts current owner head fails closed`() = runBlocking {
        val fixture = fixture()
        fixture.hideCommittedOperation = true
        assertTrue(
            runCatching {
                fixture.coordinator().promote(fixture.session, fixture.operationId, fixture.wallet)
            }.isFailure
        )
        assertTrue(fixture.journal.read(fixture.operationId, fixture.scope)!!.commitAttemptRecorded)
        assertEquals(1, fixture.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })
    }

    @Test
    fun `missing Drive generation and failed original-key proof cannot request a grant`() = runBlocking {
        val missing = fixture()
        missing.driveMissing = true
        assertEquals(
            PasskeyBackupGenerationPromotionResult.AwaitingDriveReadback,
            missing.coordinator().promote(missing.session, missing.operationId, missing.wallet)
        )
        assertEquals(0, missing.ownerRequests.count { it.url.endsWith("/owner/backup/grant") })
        assertEquals(0, missing.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })

        val failed = fixture()
        failed.proofFails = true
        assertTrue(runCatching { failed.coordinator().promote(failed.session, failed.operationId, failed.wallet) }.isFailure)
        assertEquals(0, failed.ownerRequests.count { it.url.endsWith("/owner/backup/grant") })
        assertFalse(failed.journal.read(failed.operationId, failed.scope)!!.commitAttemptRecorded)
    }

    @Test
    fun `head or selected account changing during local verification denies commit`() = runBlocking {
        val changedHead = fixture()
        changedHead.changeHeadAfterProof = true
        assertTrue(
            runCatching {
                changedHead.coordinator().promote(changedHead.session, changedHead.operationId, changedHead.wallet)
            }.isFailure
        )
        assertEquals(0, changedHead.ownerRequests.count { it.url.endsWith("/owner/backup/commit") })

        val changedAccount = fixture()
        changedAccount.changeAccountAfterProof = true
        assertTrue(
            runCatching {
                changedAccount.coordinator().promote(changedAccount.session, changedAccount.operationId, changedAccount.wallet)
            }.isFailure
        )
        assertEquals(0, changedAccount.ownerRequests.count { it.url.endsWith("/owner/backup/grant") })
        assertFalse(changedAccount.journal.read(changedAccount.operationId, changedAccount.scope)!!.commitAttemptRecorded)
    }

    @Test
    fun `compiled disable rejects promotion before network or journal work`() = runBlocking {
        val fixture = fixture()
        assertTrue(
            runCatching {
                fixture.coordinator(enabled = false).promote(fixture.session, fixture.operationId, fixture.wallet)
            }.isFailure
        )
        assertTrue(fixture.ownerRequests.isEmpty())
        assertTrue(fixture.driveRequests.isEmpty())
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)
    }

    private fun fixture(): Fixture {
        val parent = Files.createTempDirectory("backup-promotion-test").toRealPath()
        roots.add(parent)
        return Fixture(parent)
    }

    private class Fixture(parent: Path) {
        val root: Path = parent.resolve("journal")
        val journal = PasskeyBackupGenerationJournal(root, HostJournalDurability())
        val candidate = GoogleDrivePasskeyBackupGenerationStorage.Candidate(
            "allocated-file", GenerationFixture.context, GenerationFixture.bytes
        )
        val scope = PasskeyBackupJournalEntry.Scope(
            candidate.context.ownerSubject, candidate.context.backupNamespace, candidate.context.storageAccountBinding
        )
        val operationId = identifier(9)
        val wallet = PasskeyBackupExpectedWalletIdentity("wallet-1234", "wallet-001", "b".repeat(64))
        val session = PasskeyBackupOwnerSession(
            "session.${identifier(10)}", scope.ownerSubject, scope.backupNamespace, 0, "android", NOW / 1_000 + 599
        )
        val oldPrevious = PasskeyBackupHeadDescriptor(
            5, 4, "c".repeat(64), identifier(4), "a".repeat(64), 7, "old-drive-previous", scope.storageAccountBinding
        )
        val oldHead = PasskeyBackupHeadDescriptor(
            6, 5, "a".repeat(64), identifier(5), "a".repeat(64), 7, "old-drive-current", scope.storageAccountBinding
        )
        val candidateHead = PasskeyBackupHeadDescriptor(
            7, 6, "a".repeat(64), candidate.context.generationId, candidate.sha256, 7,
            candidate.fileId, scope.storageAccountBinding
        )
        var remoteHead: PasskeyBackupHeadDescriptor = oldHead
        var remotePrevious: PasskeyBackupHeadDescriptor? = oldPrevious
        var committed = false
        var loseCommitResponse = false
        var applyCommit = true
        var substituteStatus = false
        var hideCommittedOperation = false
        var driveMissing = false
        var driveMissingAfterCommit = false
        var advanceHeadDuringCommittedRead = false
        var advanceHeadDuringFinalAccountCheck = false
        var proofFails = false
        var changeHeadAfterProof = false
        var changeAccountAfterProof = false
        var selectedSubject = GOOGLE_SUBJECT
        var nowMillis = NOW
        var expireOnFinalAccountCheck = false
        var headsAfterCommit = 0
        var accessesAfterSecondHead = 0
        val events = mutableListOf<String>()
        val ownerRequests = mutableListOf<GoogleDriveHttpRequest>()
        val driveRequests = mutableListOf<GoogleDriveHttpRequest>()
        private val tokens = GoogleDriveAccessTokenProvider {
            if (headsAfterCommit >= 2) {
                accessesAfterSecondHead++
                if (expireOnFinalAccountCheck && accessesAfterSecondHead == 4) {
                    nowMillis = session.expiresAt * 1_000L
                }
                if (advanceHeadDuringFinalAccountCheck && accessesAfterSecondHead == 4) {
                    remotePrevious = candidateHead
                    remoteHead = PasskeyBackupHeadDescriptor(
                        8, 7, candidate.sha256, identifier(14), "f".repeat(64), 7,
                        "newer-drive", scope.storageAccountBinding
                    )
                }
            }
            GoogleDriveAccountAccess(selectedSubject, "selected@example.com", "drive-token")
        }
        private val driveTransport = object : GoogleDriveHttpTransport {
            override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                driveRequests += request
                return when {
                    request.method == "POST" -> response(200, driveMetadata())
                    driveMissing || driveMissingAfterCommit && committed -> response(404, "{}")
                    request.url.endsWith("?alt=media") -> {
                        events += "drive-media"
                        if (committed && advanceHeadDuringCommittedRead) {
                            remotePrevious = candidateHead
                            remoteHead = PasskeyBackupHeadDescriptor(
                                8, 7, candidate.sha256, identifier(14), "f".repeat(64), 7,
                                "newer-drive", scope.storageAccountBinding
                            )
                        }
                        GoogleDriveHttpResponse(200, candidate.bytes)
                    }
                    else -> response(200, driveMetadata())
                }
            }
        }
        private val ownerTransport = object : GoogleDriveHttpTransport {
            override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                ownerRequests += request
                assertFalse(request.headers.values.any { it.contains("drive-token") })
                return when {
                    request.url.endsWith("/owner/backup/head") -> {
                        if (committed) headsAfterCommit++
                        response(200, ownerHeadResponse())
                    }
                    request.url.endsWith("/owner/backup/operation") ->
                        response(
                            200,
                            if (committed && !hideCommittedOperation) {
                                committedStatus(substituteStatus)
                            } else {
                                "{\"status\":\"absent\"}"
                            }
                        )
                    request.url.endsWith("/owner/backup/grant") -> {
                        events += "grant"
                        response(200, "{\"token\":\"grant.${identifier(11)}\",\"expiresAt\":${NOW / 1_000 + 59}}")
                    }
                    request.url.endsWith("/owner/backup/commit") -> {
                        events += "commit"
                        assertTrue(journal.read(operationId, scope)!!.commitAttemptRecorded)
                        if (applyCommit) {
                            committed = true
                            remotePrevious = oldHead
                            remoteHead = candidateHead
                        }
                        if (loseCommitResponse) throw IOException("synthetic lost owner response")
                        response(200, committedStatus())
                    }
                    else -> error("Unexpected owner request")
                }
            }
        }
        private val storage = GoogleDrivePasskeyBackupGenerationStorage(GOOGLE_SUBJECT, tokens, driveTransport)
        private val headClient = PasskeyBackupOwnerHeadHttpClient(
            tokens, ownerTransport, nowMillis = { nowMillis }, isReleaseEnabled = true
        )
        private val generationClient = PasskeyBackupOwnerGenerationHttpClient(
            tokens, ownerTransport, nowMillis = { nowMillis }, isReleaseEnabled = true
        )

        init { journal.persistPrepared(operationId, candidate, scope) }

        fun coordinator(
            journal: PasskeyBackupGenerationJournal = this.journal,
            enabled: Boolean = true
        ): PasskeyBackupVerifiedGenerationPromotion {
            val verifier = PasskeyBackupGenerationLocalVerifier { generation, expected ->
                events += "wallet-proof"
                if (changeHeadAfterProof) {
                    remoteHead = PasskeyBackupHeadDescriptor(
                        6, 5, "a".repeat(64), identifier(12), "e".repeat(64), 7,
                        "rival-drive", scope.storageAccountBinding
                    )
                }
                if (changeAccountAfterProof) selectedSubject = "another-google-subject"
                if (proofFails) error("Original-key export failed")
                PasskeyBackupGenerationCryptographicVerifier(
                    PasskeyBackupPlaintextWalletVerifier { plaintext, identity ->
                        assertEquals("cross-platform-passkey-backup", plaintext.toString(Charsets.UTF_8))
                        PasskeyBackupLocalWalletEvidence(
                            identity.storageKey, identity.walletId, identity.publicIdentitySha256,
                            decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
                        )
                    }
                ).verify(generation, GenerationFixture.json["credentialId"].asString, ByteArray(32) { 0x66 }, expected)
            }
            return PasskeyBackupVerifiedGenerationPromotion(
                headClient, generationClient, storage, journal,
                PasskeyBackupGenerationLocalVerifierFactory { verifier },
                nowMillis = { nowMillis }, isReleaseEnabled = enabled
            )
        }

        private fun ownerHeadResponse() = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("ownerSubject", scope.ownerSubject)
            addProperty("backupNamespace", scope.backupNamespace)
            add("head", descriptor(remoteHead))
            add("previous", remotePrevious?.let(::descriptor) ?: JsonNull.INSTANCE)
        }.toString()

        private fun committedStatus(substitute: Boolean = false) = JsonObject().apply {
            addProperty("status", "committed")
            add(
                "descriptor",
                descriptor(candidateHead).apply {
                    if (substitute) addProperty("generationId", identifier(13))
                }
            )
        }.toString()

        private fun descriptor(value: PasskeyBackupHeadDescriptor) = JsonObject().apply {
            addProperty("headRevision", value.headRevision.toString())
            addProperty("parentHeadRevision", value.parentHeadRevision.toString())
            add("parentHeadSha256", value.parentHeadSha256?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            addProperty("generationId", value.generationId)
            addProperty("bundleSha256", value.bundleSha256)
            addProperty("keyEpoch", value.keyEpoch.toString())
            addProperty("driveFileId", value.driveFileId)
            addProperty("storageAccountBinding", value.storageAccountBinding)
        }

        private fun driveMetadata() = JsonObject().apply {
            addProperty("id", candidate.fileId)
            addProperty("name", "fearless-passkey-generation-${candidate.context.generationId}.bin")
            addProperty("mimeType", "application/octet-stream")
            addProperty("size", candidate.size.toString())
            add("spaces", JsonArray().apply { add("appDataFolder") })
            add(
                "appProperties",
                JsonObject().apply {
                    addProperty("format", "FPBKGEN1")
                    addProperty("namespaceSha256", PasskeyBackupGenerationFormat.sha256(scope.backupNamespace.toByteArray()))
                    addProperty("generationId", candidate.context.generationId)
                    addProperty("bundleSha256", candidate.sha256)
                }
            )
        }.toString()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val GOOGLE_SUBJECT = "google-subject-123"
        fun identifier(value: Int): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { value.toByte() })
        fun response(code: Int, text: String) = GoogleDriveHttpResponse(code, text.toByteArray(Charsets.UTF_8))
    }
}
