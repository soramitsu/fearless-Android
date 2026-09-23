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
    fun `acknowledged lost cancelled and account changed admissions never POST again after reopen`() = runBlocking {
        for (mode in listOf("acknowledged", "lost", "cancelled", "account")) {
            val fixture = fixture().apply {
                if (mode == "lost") postFailure = IOException("synthetic lost response")
                if (mode == "cancelled") postFailure = CancellationException("synthetic cancellation")
                if (mode == "account") changedSubjectAt = 1
            }
            runCatching { fixture.create(fixture.candidate()) }
            val reopened = PasskeyBackupGenerationJournal(fixture.root, HostJournalDurability())
            assertTrue(requireNotNull(reopened.read(fixture.operation, fixture.scope)).createAttemptRecorded)
            fails { fixture.storage.createCandidate(fixture.operation, reopened, fixture.scope) }
            assertEquals(if (mode == "account") 0 else 1, fixture.requests.count { it.method == "POST" })
            assertEquals(1, fixture.accesses)
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
        assertEquals(0, fixture.accesses)
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

    private class Fixture(val parent: Path) {
        val operation = JournalFixture.identifier(1)
        val scope = JournalFixture.scope
        val root: Path = parent.resolve("journal")
        val journal = PasskeyBackupGenerationJournal(root, HostJournalDurability())

        val requests = mutableListOf<GoogleDriveHttpRequest>()
        var accesses = 0
        var changedSubjectAt = Int.MAX_VALUE
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
