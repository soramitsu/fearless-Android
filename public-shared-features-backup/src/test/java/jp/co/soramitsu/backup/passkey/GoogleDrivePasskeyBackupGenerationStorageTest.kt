package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GoogleDrivePasskeyBackupGenerationStorageTest {
    @Test
    fun `allocate create and download uses same subject only immutable exact ID paths`() = runBlocking {
        val fixture = Fixture()
        val id = fixture.storage.allocateFileId()
        assertEquals("allocated-file", id)
        val candidate = fixture.storage.prepareCandidate(id, GenerationFixture.generation())
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.ACKNOWLEDGED, fixture.storage.createCandidate(candidate))
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
        val fixture = Fixture()
        val candidate = fixture.candidate()
        fixture.postFailure = IOException("synthetic lost response")
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.storage.createCandidate(candidate))
        val downloaded = fixture.storage.readCandidate(candidate.fileId, candidate.context, candidate.sha256)
        assertArrayEquals(candidate.bytes, PasskeyBackupGenerationFormat.encode(requireNotNull(downloaded)))
        assertEquals(listOf("POST", "GET", "GET"), fixture.requests.map { it.method })
        assertFalse(fixture.requests.any { it.url.contains("generateIds") })
    }

    @Test
    fun `409 and even malformed success require exact downloaded digest`() = runBlocking {
        for (status in listOf(409, 500, 401, 403, 301, 302, 307, 308)) {
            val fixture = Fixture().apply { postCode = status }
            val candidate = fixture.candidate()
            assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.storage.createCandidate(candidate))
            assertEquals(1, fixture.requests.size)
        }
        val fixture = Fixture().apply { postMetadata = "{}".toByteArray() }
        assertEquals(GoogleDrivePasskeyBackupGenerationStorage.CreateOutcome.RECONCILE_REQUIRED, fixture.storage.createCandidate(fixture.candidate()))
        fixture.media = GenerationFixture.bytes.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
    }

    @Test
    fun `cancellation does not retry delete or declare upload complete`() = runBlocking {
        val fixture = Fixture().apply { postFailure = CancellationException("synthetic cancellation") }
        val failure = runCatching { fixture.storage.createCandidate(fixture.candidate()) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(listOf("POST"), fixture.requests.map { it.method })
    }

    @Test
    fun `observed missing content is not an automatic recreate or delete`() = runBlocking {
        for (atMetadata in listOf(true, false)) {
            val fixture = Fixture().apply { if (atMetadata) metadataCode = 404 else mediaCode = 404 }
            assertEquals(null, fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest))
            assertTrue(fixture.requests.all { it.method == "GET" })
            assertEquals(if (atMetadata) 1 else 2, fixture.requests.size)
        }
    }

    @Test
    fun `account switch at every network boundary blocks the switched bearer`() = runBlocking {
        for (switchAt in 1..4) {
            val fixture = Fixture().apply { changedSubjectAt = switchAt }
            val failure = runCatching {
                val id = fixture.storage.allocateFileId()
                val candidate = fixture.storage.prepareCandidate(id, GenerationFixture.generation())
                fixture.storage.createCandidate(candidate)
                fixture.storage.readCandidate(id, candidate.context, candidate.sha256)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(switchAt - 1, fixture.requests.size)
            assertFalse(fixture.requests.any { it.headers["Authorization"] == "Bearer wrong-token" })
        }
    }

    @Test
    fun `unsafe file IDs foreign storage context and malformed digest never request token`() = runBlocking {
        val fixture = Fixture()
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
            val fixture = Fixture().apply { allocation = body.toByteArray() }
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
            val fixture = Fixture().apply { returnedMetadata = json.toString().toByteArray() }
            fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
            assertEquals(1, fixture.requests.size)
        }
        val fixture = Fixture().apply { media = GenerationFixture.bytes + 0 }
        fails { fixture.storage.readCandidate("allocated-file", GenerationFixture.context, GenerationFixture.digest) }
    }

    private suspend fun fails(block: suspend () -> Any?) = assertTrue(runCatching { block() }.isFailure)

    private class Fixture {
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
