package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class GoogleDrivePasskeyBackupGenerationStorageTest {
    private val journals = mutableListOf<Path>()

    @After
    fun removeSyntheticJournals() { journals.forEach { it.toFile().deleteRecursively() } }

    private fun fixture(): Fixture {
        val parent = Files.createTempDirectory("generation-upload-journal").toRealPath()
        journals.add(parent)
        return Fixture(parent)
    }

    @Test
    fun `allocate create and download uses same subject only immutable exact ID paths`() = runBlocking {
        val fixture = fixture()
        val id = fixture.storage.allocateFileId()
        assertEquals("allocated-file", id)
        val candidate = fixture.storage.prepareCandidate(id, GenerationFixture.generation())
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.ACKNOWLEDGED, fixture.create(candidate))
        val downloaded = fixture.storage.readCandidate(id, candidate.context, candidate.sha256)
        assertArrayEquals(candidate.bytes, PasskeyBackupGenerationFormat.encode(requireNotNull(downloaded)))
        assertEquals(listOf("GET", "POST", "GET", "GET"), fixture.requests.map { it.method })
        assertEquals("https://www.googleapis.com/drive/v3/files/generateIds?count=1&space=appDataFolder&type=files", fixture.requests[0].url)
        assertTrue(fixture.requests[1].url.startsWith("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields="))
        assertTrue(fixture.requests[1].isOneShot)
        val multipart = requireNotNull(fixture.requests[1].body).toString(Charsets.ISO_8859_1)
        assertTrue(multipart.contains("\"id\":\"allocated-file\""))
        assertTrue(multipart.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(multipart.contains(candidate.bytes.toString(Charsets.ISO_8859_1)))
        assertEquals(4, fixture.accesses)
        val sameBearer = fixture.requests.all {
            it.headers["Authorization"] == "Bearer synthetic-token" && it.headers["Cache-Control"] == "no-store"
        }
        assertTrue(sameBearer)
        assertFalse(candidate.toString().contains(candidate.fileId))
        candidate.bytes.fill(0)
        assertEquals(GenerationFixture.digest, candidate.sha256)
        assertArrayEquals(GenerationFixture.bytes, candidate.bytes)
    }

    @Test
    fun `authenticated owner head selects exact committed Drive generation`() = runBlocking {
        val fixture = fixture()
        val candidate = fixture.candidate()
        val committed = authenticatedHead(candidate)
        val selected = committed.currentReadParameters()
        assertEquals(candidate.fileId, selected.fileId)
        assertEquals(candidate.context, selected.context)
        assertEquals(candidate.sha256, selected.sha256)
        val downloaded = fixture.storage.readCurrentHead(committed)
        assertArrayEquals(candidate.bytes, PasskeyBackupGenerationFormat.encode(requireNotNull(downloaded)))
        assertEquals(listOf("GET", "GET"), fixture.requests.map { it.method })
        assertTrue(fixture.requests.all { it.url.contains("/files/${candidate.fileId}?") })
        assertEquals("PasskeyBackupAuthenticatedHead(redacted)", committed.toString())
        assertEquals("PasskeyBackupHeadDescriptor(redacted)", committed.head.toString())
    }

    @Test
    fun `owner account parent and empty head substitutions fail before Drive`() = runBlocking {
        val fixture = fixture()
        val candidate = fixture.candidate()
        val valid = authenticatedHead(candidate)
        val previous = requireNotNull(valid.previous)
        val current = requireNotNull(valid.head)
        fails {
            PasskeyBackupAuthenticatedHead(
                candidate.context.ownerSubject, candidate.context.backupNamespace, current, previous,
                "owner:${"A".repeat(43)}", candidate.context.backupNamespace, candidate.context.storageAccountBinding
            )
        }
        fails {
            PasskeyBackupAuthenticatedHead(
                candidate.context.ownerSubject, candidate.context.backupNamespace, current, previous,
                candidate.context.ownerSubject, candidate.context.backupNamespace, "c".repeat(64)
            )
        }
        fails {
            PasskeyBackupAuthenticatedHead(
                candidate.context.ownerSubject, candidate.context.backupNamespace, current, null,
                candidate.context.ownerSubject, candidate.context.backupNamespace, candidate.context.storageAccountBinding
            )
        }
        val wrongParent = PasskeyBackupHeadDescriptor(
            6, 5, "b".repeat(64), "E".repeat(43), "d".repeat(64),
            candidate.context.keyEpoch, "previous-drive-id", candidate.context.storageAccountBinding
        )
        fails {
            PasskeyBackupAuthenticatedHead(
                candidate.context.ownerSubject, candidate.context.backupNamespace, current, wrongParent,
                candidate.context.ownerSubject, candidate.context.backupNamespace, candidate.context.storageAccountBinding
            )
        }
        fails {
            PasskeyBackupHeadDescriptor(
                7, 5, candidate.context.parentHeadSha256, candidate.context.generationId, candidate.sha256,
                candidate.context.keyEpoch, candidate.fileId, candidate.context.storageAccountBinding
            )
        }
        val empty = PasskeyBackupAuthenticatedHead(
            candidate.context.ownerSubject, candidate.context.backupNamespace, null, null,
            candidate.context.ownerSubject, candidate.context.backupNamespace, candidate.context.storageAccountBinding
        )
        fails { fixture.storage.readCurrentHead(empty) }
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `unknown uploaded outcome reconciles exact ID without second create or replacement`() = runBlocking {
        val fixture = fixture()
        val candidate = fixture.candidate()
        fixture.postFailure = IOException("synthetic lost response")
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.create(candidate))
        val downloaded = fixture.storage.readCandidate(candidate.fileId, candidate.context, candidate.sha256)
        assertArrayEquals(candidate.bytes, PasskeyBackupGenerationFormat.encode(requireNotNull(downloaded)))
        assertEquals(listOf("POST", "GET", "GET"), fixture.requests.map { it.method })
        assertFalse(fixture.requests.any { it.url.contains("generateIds") })
    }

    @Test
    fun `journaled unknown outcome verifies after restart without another POST`() = runBlocking {
        val fixture = fixture().apply { postFailure = IOException("synthetic lost response") }
        val candidate = fixture.candidate()
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.create(candidate))
        val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
        var verified = 0
        val reconciler = PasskeyBackupGenerationReconciler(fixture.storage) { generation, expected ->
            assertArrayEquals(candidate.bytes, PasskeyBackupGenerationFormat.encode(generation))
            assertEquals(candidate.context, generation.context)
            verified++
            evidence(expected)
        }
        val reconciled = reconciler.reconcile(fixture.operation, reopened, fixture.scope, expectedWallet())
        assertEquals(
            PasskeyBackupGenerationReconciliation.LocallyVerified(
                candidate.context.generationId, candidate.sha256, expectedWallet().publicIdentitySha256
            ),
            reconciled
        )
        assertFalse(reconciled.toString().contains(candidate.context.generationId))
        assertEquals(1, verified)
        assertEquals(listOf("POST", "GET", "GET"), fixture.requests.map { it.method })
        fails { fixture.storage.createCandidate(fixture.operation, reopened, fixture.scope) }
        assertEquals(1, fixture.requests.count { it.method == "POST" })
    }

    @Test
    fun `reconciliation refuses missing or unsent journal before Drive or local verifier`() = runBlocking {
        val fixture = fixture()
        var verified = 0
        val reconciler = PasskeyBackupGenerationReconciler(fixture.storage) { _, expected ->
            verified++
            evidence(expected)
        }
        fails { reconciler.reconcile(fixture.operation, fixture.journal, fixture.scope, expectedWallet()) }
        fixture.journal.persistPrepared(fixture.operation, fixture.candidate(), fixture.scope)
        fails { reconciler.reconcile(fixture.operation, fixture.journal, fixture.scope, expectedWallet()) }
        assertEquals(0, verified)
        assertEquals(0, fixture.accesses)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `404 wrong scope and tampered Drive bytes never produce a local verification`() = runBlocking {
        val missing = fixture().apply { metadataCode = 404 }
        missing.create(missing.candidate())
        var verified = 0
        val missingReconciler = PasskeyBackupGenerationReconciler(missing.storage) { _, expected ->
            verified++
            evidence(expected)
        }
        assertEquals(
            PasskeyBackupGenerationReconciliation.NotFound,
            missingReconciler.reconcile(missing.operation, missing.journal, missing.scope, expectedWallet())
        )
        assertEquals(listOf("POST", "GET"), missing.requests.map { it.method })
        assertEquals(0, verified)
        missing.metadataCode = 200
        val reopened = PasskeyBackupGenerationJournal(missing.root, HostJournalDurability())
        assertTrue(
            missingReconciler.reconcile(missing.operation, reopened, missing.scope, expectedWallet()) is
                PasskeyBackupGenerationReconciliation.LocallyVerified
        )
        assertEquals(listOf("POST", "GET", "GET", "GET"), missing.requests.map { it.method })
        assertEquals(1, verified)

        val tampered = fixture()
        tampered.create(tampered.candidate())
        val wrongScope = tampered.scope.copy(ownerSubject = "owner:" + JournalFixture.identifier(9))
        val before = tampered.accesses
        val reconciler = PasskeyBackupGenerationReconciler(tampered.storage) { _, expected ->
            verified++
            evidence(expected)
        }
        fails { reconciler.reconcile(tampered.operation, tampered.journal, wrongScope, expectedWallet()) }
        assertEquals(before, tampered.accesses)
        tampered.media = GenerationFixture.bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        fails { reconciler.reconcile(tampered.operation, tampered.journal, tampered.scope, expectedWallet()) }
        assertEquals(1, verified)
        assertEquals(1, tampered.requests.count { it.method == "POST" })
    }

    @Test
    fun `failed verifier cancellation and account switch cannot verify generation`() = runBlocking {
        val fixture = fixture()
        fixture.create(fixture.candidate())
        var attempts = 0
        val failing = PasskeyBackupGenerationReconciler(fixture.storage) { _, _ ->
            attempts++
            if (attempts == 1) error("synthetic decryption failure")
            throw CancellationException("synthetic verifier cancellation")
        }
        fails { failing.reconcile(fixture.operation, fixture.journal, fixture.scope, expectedWallet()) }
        fails { failing.reconcile(fixture.operation, fixture.journal, fixture.scope, expectedWallet()) }
        assertEquals(2, attempts)
        assertEquals(1, fixture.requests.count { it.method == "POST" })

        val switched = fixture()
        switched.create(switched.candidate())
        switched.changedSubjectAt = 4 // POST token, metadata GET, media GET, then final selected-account check.
        var verifierCalled = false
        val reconciler = PasskeyBackupGenerationReconciler(switched.storage) { _, expected ->
            verifierCalled = true
            evidence(expected)
        }
        fails { reconciler.reconcile(switched.operation, switched.journal, switched.scope, expectedWallet()) }
        assertTrue(verifierCalled)
        assertEquals(1, switched.requests.count { it.method == "POST" })
        assertFalse(switched.requests.any { it.headers["Authorization"] == "Bearer wrong-token" })
    }

    @Test
    fun `wrong wallet and false local signing or export evidence cannot verify`() = runBlocking {
        val fixture = fixture()
        fixture.create(fixture.candidate())
        var calls = 0
        val verifier = PasskeyBackupGenerationReconciler(fixture.storage) { _, expected ->
            calls++
            evidence(expected)
        }
        val wrongWallet = PasskeyBackupExpectedWalletIdentity(
            "wallet-1234", "wallet-other", expectedWallet().publicIdentitySha256
        )
        fails { verifier.reconcile(fixture.operation, fixture.journal, fixture.scope, wrongWallet) }
        assertEquals(0, calls)
        for (failedCheck in 0..3) {
            val wrongEvidence = PasskeyBackupGenerationReconciler(fixture.storage) { _, expected ->
                calls++
                PasskeyBackupLocalWalletEvidence(
                    expected.storageKey, expected.walletId,
                    if (failedCheck == 3) "c".repeat(64) else expected.publicIdentitySha256,
                    decryptionVerified = failedCheck != 0,
                    originalKeySigningVerified = failedCheck != 1,
                    originalKeyExportVerified = failedCheck != 2
                )
            }
            fails { wrongEvidence.reconcile(fixture.operation, fixture.journal, fixture.scope, expectedWallet()) }
        }
        assertEquals(4, calls)
        assertEquals(1, fixture.requests.count { it.method == "POST" })
    }

    @Test
    fun `409 and even malformed success require exact downloaded digest`() = runBlocking {
        for (status in listOf(409, 500, 401, 403, 301, 302, 307, 308)) {
            val fixture = fixture().apply { postCode = status }
            val candidate = fixture.candidate()
            assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.create(candidate))
            assertEquals(1, fixture.requests.size)
        }
        val fixture = fixture().apply { postMetadata = "{}".toByteArray() }
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.create(fixture.candidate()))
        fixture.media = GenerationFixture.bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
    }

    @Test
    fun `cancellation does not retry delete or declare upload complete`() = runBlocking {
        val fixture = fixture().apply { postFailure = CancellationException("synthetic cancellation") }
        val failure = runCatching { fixture.create(fixture.candidate()) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(listOf("POST"), fixture.requests.map { it.method })
    }

    @Test
    fun `missing wrong scope and corrupt attempt cannot reach token or POST`() = runBlocking {
        val fixture = fixture()
        fails { fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope) }
        fixture.journal.persistPrepared(fixture.operation, fixture.candidate(), fixture.scope)
        for (wrong in listOf(
            fixture.scope.copy(ownerSubject = "owner:" + JournalFixture.identifier(2)),
            fixture.scope.copy(backupNamespace = "backup:" + JournalFixture.identifier(2)),
            fixture.scope.copy(storageAccountBinding = "bb".repeat(32))
        )) {
            fails { fixture.storage.createCandidate(fixture.operation, fixture.journal, wrong) }
        }
        val marker = fixture.root.resolve(fixture.operation + PasskeyBackupJournalDisk.ATTEMPT_SUFFIX)
        assertFalse(Files.exists(marker))
        Files.write(marker, byteArrayOf(1))
        Files.setPosixFilePermissions(marker, PosixFilePermissions.fromString("rw-------"))
        fails { fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope) }
        assertEquals(0, fixture.accesses)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `acknowledged lost and cancelled admissions never POST again after reopen`() = runBlocking {
        for (mode in listOf("acknowledged", "lost", "cancelled")) {
            val fixture = fixture().apply {
                if (mode == "lost") postFailure = IOException("synthetic lost response")
                if (mode == "cancelled") postFailure = CancellationException("synthetic cancellation")
            }
            runCatching { fixture.create(fixture.candidate()) }
            val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
            assertTrue(requireNotNull(reopened.read(fixture.operation, fixture.scope)).createAttemptRecorded)
            fails { fixture.storage.createCandidate(fixture.operation, reopened, fixture.scope) }
            assertEquals(1, fixture.requests.count { it.method == "POST" })
            assertEquals(1, fixture.accesses)
        }
    }

    @Test
    fun `account or token failure before admission keeps exact candidate retryable`() = runBlocking {
        for (mode in listOf("account", "token")) {
            val fixture = fixture().apply {
                if (mode == "account") changedSubjectAt = 1 else tokenFailureAt = 1
            }
            fixture.journal.persistPrepared(fixture.operation, fixture.candidate(), fixture.scope)
            fails { fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope) }
            assertFalse(requireNotNull(fixture.journal.read(fixture.operation, fixture.scope)).createAttemptRecorded)
            assertTrue(fixture.requests.isEmpty())
            fixture.changedSubjectAt = Int.MAX_VALUE
            fixture.tokenFailureAt = Int.MAX_VALUE
            assertEquals(
                GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.ACKNOWLEDGED,
                fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope)
            )
            assertEquals(listOf("POST"), fixture.requests.map { it.method })
            assertEquals(2, fixture.accesses)
        }
    }

    @Test
    fun `durable marker acknowledgement failure never reaches POST and denies later admission`() = runBlocking {
        val fixture = fixture()
        fixture.journal.persistPrepared(fixture.operation, fixture.candidate(), fixture.scope)
        val journal = PasskeyBackupGenerationJournal(
            fixture.root,
            HostJournalDurability {
                if (it == JournalDurabilityPoint.ATTEMPT_DIRECTORY_SYNCED) error("synthetic sync acknowledgement loss")
            }
        )
        fails { fixture.storage.createCandidate(fixture.operation, journal, fixture.scope) }
        assertTrue(requireNotNull(fixture.journal.read(fixture.operation, fixture.scope)).createAttemptRecorded)
        fails { fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope) }
        assertEquals(1, fixture.accesses)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `competing upload callers admit exactly one durable POST`() = runBlocking {
        val fixture = fixture()
        fixture.journal.persistPrepared(fixture.operation, fixture.candidate(), fixture.scope)
        val results = (1..2).map {
            async { runCatching { fixture.storage.createCandidate(fixture.operation, fixture.journal, fixture.scope) } }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(listOf("POST"), fixture.requests.map { it.method })
    }

    @Test
    fun `observed missing content is not an automatic recreate or delete`() = runBlocking {
        for (atMetadata in listOf(true, false)) {
            val fixture = fixture().apply { if (atMetadata) metadataCode = 404 else mediaCode = 404 }
            assertEquals(null, fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest))
            assertTrue(fixture.requests.all { it.method == "GET" })
            assertEquals(if (atMetadata) 1 else 2, fixture.requests.size)
        }
    }

    @Test
    fun `account switch at every network boundary blocks the switched bearer`() = runBlocking {
        for (switchAt in 1..4) {
            val fixture = fixture().apply { changedSubjectAt = switchAt }
            val failure = runCatching {
                val id = fixture.storage.allocateFileId()
                val candidate = fixture.storage.prepareCandidate(id, GenerationFixture.generation())
                fixture.create(candidate)
                fixture.storage.readCandidate(id, candidate.context, candidate.sha256)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(switchAt - 1, fixture.requests.size)
            assertFalse(fixture.requests.any { it.headers["Authorization"] == "Bearer wrong-token" })
        }
    }

    @Test
    fun `unsafe file IDs foreign storage context and malformed digest never request token`() = runBlocking {
        val fixture = fixture()
        for (id in listOf("", "../file", "file?redirect", "a".repeat(257))) {
            fails { fixture.storage.prepareCandidate(id, GenerationFixture.generation()) }
            fails { fixture.storage.readCandidate(id, GenerationFixture.context, GenerationFixture.digest) }
        }
        val original = GenerationFixture.generation()
        val foreign = original.context.copy(storageAccountBinding = "bb".repeat(32))
        fails {
            fixture.storage.prepareCandidate("safe-id", PasskeyBackupGeneration(foreign, original.envelope, original.wrappers))
        }
        fails { fixture.storage.readCandidate("safe-id", foreign, GenerationFixture.digest) }
        fails { fixture.storage.readCandidate("safe-id", original.context, "not-sha256") }
        assertEquals(0, fixture.accesses)
    }

    @Test
    fun `malformed allocation duplicate fields excessive size and wrong space fail closed`() = runBlocking {
        for (body in listOf(
            "{}", "[]", "null", "not-json",
            """{"kind":["drive#generatedIds"],"space":"appDataFolder","ids":["file"]}""",
            """{"kind":"drive#generatedIds","space":"drive","ids":["file"]}""",
            """{"kind":"drive#generatedIds","space":"appDataFolder","ids":[]}""",
            """{"kind":"drive#generatedIds","space":"appDataFolder","ids":["a","b"]}""",
            """{"kind":"drive#generatedIds","space":"appDataFolder","ids":["a"],"space":"appDataFolder"}""",
            """{"kind":"drive#generatedIds","space":"appDataFolder","ids":["../bad"]}""",
            " ".repeat(8193)
        )) {
            val fixture = fixture().apply { allocation = body.toByteArray() }
            fails { fixture.storage.allocateFileId() }
        }
    }

    @Test
    fun `metadata cannot replace ID digest file type namespace or reported size`() = runBlocking {
        val valid = metadata()
        for (json in listOf(
            valid.deepCopy().apply { addProperty("id", "different") },
            valid.deepCopy().apply { addProperty("name", "different") },
            valid.deepCopy().apply { addProperty("mimeType", "text/plain") },
            valid.deepCopy().apply { add("spaces", JsonArray().apply { add("drive") }) },
            valid.deepCopy().apply { addProperty("size", "0785") },
            valid.deepCopy().apply { add("size", JsonArray().apply { add("785") }) },
            valid.deepCopy().apply { addProperty("size", "524289") },
            valid.deepCopy().apply { getAsJsonObject("appProperties").addProperty("bundleSha256", "bb".repeat(32)) },
            valid.deepCopy().apply { getAsJsonObject("appProperties").addProperty("namespaceSha256", "bb".repeat(32)) }
        )) {
            val fixture = fixture().apply { returnedMetadata = json.toString().toByteArray() }
            fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
            assertEquals(1, fixture.requests.size)
        }
        val fixture = fixture().apply { media = GenerationFixture.bytes + 0 }
        fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
    }

    private suspend fun fails(block: suspend () -> Any?) = assertTrue(runCatching { block() }.isFailure)

    private fun expectedWallet() = PasskeyBackupExpectedWalletIdentity(
        "wallet-1234", "wallet-001", "b".repeat(64)
    )

    private fun evidence(expected: PasskeyBackupExpectedWalletIdentity) = PasskeyBackupLocalWalletEvidence(
        expected.storageKey, expected.walletId, expected.publicIdentitySha256,
        decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
    )

    private fun authenticatedHead(
        candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate
    ): PasskeyBackupAuthenticatedHead {
        val context = candidate.context
        val previous = PasskeyBackupHeadDescriptor(
            6, 5, "b".repeat(64), "E".repeat(43), "a".repeat(64),
            context.keyEpoch, "previous-drive-id", context.storageAccountBinding
        )
        val current = PasskeyBackupHeadDescriptor(
            7, 6, context.parentHeadSha256, context.generationId, candidate.sha256,
            context.keyEpoch, candidate.fileId, context.storageAccountBinding
        )
        return PasskeyBackupAuthenticatedHead(
            context.ownerSubject, context.backupNamespace, current, previous,
            context.ownerSubject, context.backupNamespace, context.storageAccountBinding
        )
    }

    private class Fixture(val parent: Path) {
        val operation = JournalFixture.identifier(1)
        val scope = JournalFixture.scope
        val root: Path = parent.resolve("journal")
        val journal = PasskeyBackupGenerationJournal(root, HostJournalDurability())

        val requests = mutableListOf<GoogleDriveHttpRequest>()
        var accesses = 0
        var changedSubjectAt = Int.MAX_VALUE
        var tokenFailureAt = Int.MAX_VALUE
        var postFailure: Exception? = null
        var postCode = 201
        var metadataCode = 200
        var mediaCode = 200
        var allocation = """{"kind":"drive#generatedIds","space":"appDataFolder","ids":["allocated-file"]}""".toByteArray()
        var postMetadata: ByteArray? = null
        var returnedMetadata = metadata().toString().toByteArray()
        var media = GenerationFixture.bytes
        val storage = GoogleDrivePasskeyBackupGenerationStorage(
            "google-subject-123",
            GoogleDriveAccessTokenProvider {
                accesses++
                if (accesses == tokenFailureAt) throw IOException("synthetic token failure")
                if (accesses == changedSubjectAt) {
                    GoogleDriveAccountAccess("other-subject", "renamed@example.com", "wrong-token")
                } else {
                    GoogleDriveAccountAccess("google-subject-123", "renamed@example.com", "synthetic-token")
                }
            },
            object : GoogleDriveHttpTransport {
                override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
                    requests += request
                    return when {
                        request.url.contains("generateIds") -> GoogleDriveHttpResponse(200, allocation)
                        request.method == "POST" -> {
                            postFailure?.let { throw it }
                            GoogleDriveHttpResponse(postCode, postMetadata ?: returnedMetadata)
                        }
                        request.url.endsWith("?alt=media") -> GoogleDriveHttpResponse(mediaCode, media)
                        else -> GoogleDriveHttpResponse(metadataCode, returnedMetadata)
                    }
                }
            }
        )

        suspend fun create(
            candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate
        ): GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome {
            journal.persistPrepared(operation, candidate, scope)
            return storage.createCandidate(operation, journal, scope)
        }

        fun candidate() = storage.prepareCandidate("allocated-file", GenerationFixture.generation())
    }

    companion object {
        private fun metadata() = JsonObject().apply {
            addProperty("id", "allocated-file")
            addProperty("name", "fearless-passkey-generation-${GenerationFixture.context.generationId}.bin")
            addProperty("mimeType", "application/octet-stream")
            addProperty("size", "785")
            add("spaces", JsonArray().apply { add("appDataFolder") })
            add(
                "appProperties",
                JsonObject().apply {
                addProperty("format", "FPBKGEN1")
                addProperty("namespaceSha256", PasskeyBackupGenerationFormat.sha256(GenerationFixture.context.backupNamespace.toByteArray()))
                addProperty("generationId", GenerationFixture.context.generationId)
                addProperty("bundleSha256", GenerationFixture.digest)
            }
            )
        }
    }
}
