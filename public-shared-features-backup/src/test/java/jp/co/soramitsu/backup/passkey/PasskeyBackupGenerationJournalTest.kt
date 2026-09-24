package jp.co.soramitsu.backup.passkey

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class PasskeyBackupGenerationJournalTest {
    private val parent = Files.createTempDirectory("backup-journal-test").toRealPath()
    private val root = parent.resolve("journal")
    private val scope = JournalFixture.scope
    private val operation = JournalFixture.identifier(1)

    @After
    fun cleanup() { parent.toFile().deleteRecursively() }

    @Test
    fun `prepared ciphertext reloads exactly and only one create attempt is admitted across restart`() {
        val candidate = JournalFixture.candidate()
        val entry = journal().persistPrepared(operation, candidate, scope)
        assertFalse(entry.createAttemptRecorded)
        assertArrayEquals(candidate.bytes, requireNotNull(journal().read(operation, scope)).candidate.bytes)
        assertEquals(listOf(operation), journal().listPending(scope).map { it.operationId })
        assertTrue(journal().markCreateAttempt(operation, scope).createAttemptRecorded)
        assertTrue(requireNotNull(journal().read(operation, scope)).createAttemptRecorded)
        fails { journal().markCreateAttempt(operation, scope) }
        assertTrue(journal().persistPrepared(operation, candidate, scope).createAttemptRecorded)
        assertNull(journal().read(JournalFixture.identifier(2), scope))
    }

    @Test
    fun `record and attempt bytes are private immutable bounded and contain no plaintext key or PRF`() {
        val entry = journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val record = Files.readAllBytes(preparedPath())
        assertEquals(entry.recordSha256, PasskeyBackupGenerationFormat.sha256(record))
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(preparedPath()))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
        val text = record.toString(Charsets.UTF_8)
        listOf(
            "cross-platform-passkey-backup", "alice@example.com", Base64.getUrlEncoder().encodeToString(ByteArray(32) { 0x66 }),
            Base64.getUrlEncoder().encodeToString(ByteArray(32) { 0x77 })
        ).forEach { assertFalse(text.contains(it)) }
        assertEquals("PasskeyBackupJournalEntry(redacted)", entry.toString())
        assertEquals("PasskeyBackupJournalEntry.Scope(redacted)", scope.toString())
        journal().markCreateAttempt(operation, scope)
        assertTrue(Files.size(attemptPath()) <= PasskeyBackupJournalRecord.MAX_ATTEMPT_BYTES)
        assertArrayEquals(record, Files.readAllBytes(preparedPath()))
    }

    @Test
    fun `same operation is idempotent but conflicting bytes and alternate operation retries reject`() {
        val candidate = JournalFixture.candidate()
        journal().persistPrepared(operation, candidate, scope)
        val original = Files.readAllBytes(preparedPath())
        journal().persistPrepared(operation, candidate, scope)
        fails { journal().persistPrepared(operation, JournalFixture.candidate(2), scope) }
        journal().markCreateAttempt(operation, scope)
        fails { journal().persistPrepared(JournalFixture.identifier(2), candidate, scope) }
        val sameFile = JournalFixture.candidate(2, fileId = candidate.fileId)
        fails { journal().persistPrepared(JournalFixture.identifier(2), sameFile, scope) }
        val sameGeneration = GoogleDrivePasskeyBackupGenerationStorage.Candidate("other-file", candidate.context, candidate.bytes)
        fails { journal().persistPrepared(JournalFixture.identifier(2), sameGeneration, scope) }
        assertArrayEquals(original, Files.readAllBytes(preparedPath()))
        assertEquals(1, journal().listPending(scope).size)
    }

    @Test
    fun `owner namespace and verified Google account scope cannot be inferred from disk`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        for (wrong in listOf(
            scope.copy(ownerSubject = "owner:" + JournalFixture.identifier(99)),
            scope.copy(backupNamespace = "backup:" + JournalFixture.identifier(99)), scope.copy(storageAccountBinding = "bb".repeat(32))
        )) {
            fails { journal().read(operation, wrong) }
            fails { journal().markCreateAttempt(operation, wrong) }
            fails { journal().persistPrepared(JournalFixture.identifier(2), JournalFixture.candidate(2), wrong) }
            assertTrue(journal().listPending(wrong).isEmpty())
        }
        assertFalse(Files.exists(attemptPath()))
    }

    @Test
    fun `canonical record rejects truncation duplicate keys coercion digest mismatch and oversized input`() {
        val bytes = PasskeyBackupJournalRecord.encode(operation, JournalFixture.candidate())
        val text = bytes.toString(Charsets.UTF_8)
        val bad = listOf(
            bytes.copyOf(bytes.size - 1), bytes + 0, ByteArray(PasskeyBackupJournalRecord.MAX_BYTES + 1),
            text.replace("\"operationId\":", "\"operationId\":\"${JournalFixture.identifier(2)}\",\"\\u006fperationId\":").toByteArray(),
            text.replace("\"bundleSize\":\"785\"", "\"bundleSize\":785").toByteArray(),
            text.replace("\"bundleSize\":\"785\"", "\"bundleSize\":\"0785\"").toByteArray(),
            text.replace(GenerationFixture.digest, "bb".repeat(32)).toByteArray(),
            text.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0").toByteArray(),
            text.replace("\"parentHeadRevision\":\"6\"", "\"parentHeadRevision\":\"9223372036854775808\"").toByteArray()
        )
        bad.forEach { fails { PasskeyBackupJournalRecord.decode(it, operation) } }
        fails { PasskeyBackupJournalRecord.decode(bytes, JournalFixture.identifier(2)) }
        assertArrayEquals(GenerationFixture.bytes, PasskeyBackupJournalRecord.decode(bytes, operation).candidate.bytes)
    }

    @Test
    fun `partial malformed or swapped attempt markers never permit another create`() {
        val entry = journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val valid = PasskeyBackupJournalRecord.attempt(entry)
        val foreign = PasskeyBackupJournalRecord.attempt(
            PasskeyBackupJournalRecord.decode(
            PasskeyBackupJournalRecord.encode(JournalFixture.identifier(2), JournalFixture.candidate(2)), JournalFixture.identifier(2)
            )
        )
        for (bytes in listOf(byteArrayOf(), valid.copyOf(valid.size / 2), foreign, valid + 0)) {
            Files.write(attemptPath(), bytes)
            Files.setPosixFilePermissions(attemptPath(), PosixFilePermissions.fromString("rw-------"))
            fails { journal().read(operation, scope) }
            fails { journal().listPending(scope) }
            fails { journal().markCreateAttempt(operation, scope) }
            assertArrayEquals(bytes, Files.readAllBytes(attemptPath()))
        }
    }

    @Test
    fun `IO failure after durable marker leaves unknown outcome and cannot readmit`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val failing = journal(
            HostJournalDurability { point ->
            if (point == JournalDurabilityPoint.ATTEMPT_DIRECTORY_SYNCED) error("injected sync acknowledgement loss")
        }
        )
        fails { failing.markCreateAttempt(operation, scope) }
        assertTrue(requireNotNull(journal().read(operation, scope)).createAttemptRecorded)
        fails { journal().markCreateAttempt(operation, scope) }
    }

    @Test
    fun `file then directory synchronization completes before admission returns`() {
        val observed = mutableListOf<JournalDurabilityPoint>()
        val journal = journal(HostJournalDurability { observed.add(it) })
        journal.persistPrepared(operation, JournalFixture.candidate(), scope)
        journal.markCreateAttempt(operation, scope)
        assertEquals(JournalDurabilityPoint.entries.filterNot { it.name.startsWith("COMMIT") }, observed)
    }

    @Test
    fun `commit attempt marker is private durable exact and never readmitted after restart`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        fails { journal().markCommitAttempt(operation, scope) }
        journal().markCreateAttempt(operation, scope)
        val admitted = journal().markCommitAttempt(operation, scope)
        assertTrue(admitted.commitAttemptRecorded)
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(commitPath()))
        assertTrue(Files.size(commitPath()) <= PasskeyBackupJournalRecord.MAX_ATTEMPT_BYTES)
        assertTrue(requireNotNull(journal().read(operation, scope)).commitAttemptRecorded)
        fails { journal().markCommitAttempt(operation, scope) }
        fails { journal().markCreateAttempt(operation, scope) }
    }

    @Test
    fun `partial or swapped commit marker blocks read and another CAS admission`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        journal().markCreateAttempt(operation, scope)
        val valid = PasskeyBackupJournalRecord.commitAttempt(requireNotNull(journal().read(operation, scope)))
        val foreign = PasskeyBackupJournalRecord.commitAttempt(
            PasskeyBackupJournalRecord.decode(
                PasskeyBackupJournalRecord.encode(JournalFixture.identifier(2), JournalFixture.candidate(2)),
                JournalFixture.identifier(2)
            )
        )
        for (bytes in listOf(byteArrayOf(), valid.copyOf(valid.size / 2), foreign, valid + 0)) {
            Files.write(commitPath(), bytes)
            Files.setPosixFilePermissions(commitPath(), PosixFilePermissions.fromString("rw-------"))
            fails { journal().read(operation, scope) }
            fails { journal().listPending(scope) }
            fails { journal().markCommitAttempt(operation, scope) }
            assertTrue(requireNotNull(journal().readForReconciliation(operation, scope)).commitAttemptRecorded)
            assertEquals(1, journal().listForReconciliation(scope).size)
            assertArrayEquals(bytes, Files.readAllBytes(commitPath()))
        }
    }

    @Test
    fun `separate process crashes at every commit marker stage prevent second CAS admission`() {
        for (point in JournalDurabilityPoint.entries.filter { it.name.startsWith("COMMIT") }) {
            root.toFile().deleteRecursively()
            journal().persistPrepared(operation, JournalFixture.candidate(), scope)
            journal().markCreateAttempt(operation, scope)
            assertEquals(23, finish(child("commit", point)))
            val before = Files.readAllBytes(commitPath())
            fails { journal().markCommitAttempt(operation, scope) }
            assertArrayEquals(before, Files.readAllBytes(commitPath()))
        }
    }

    @Test
    fun `hard entry cap preserves existing generations and denies sixty fifth record`() {
        val journal = journal()
        repeat(64) { index -> journal.persistPrepared(JournalFixture.identifier(index), JournalFixture.candidate(index), scope) }
        fails { journal.persistPrepared(JournalFixture.identifier(64), JournalFixture.candidate(64), scope) }
        assertEquals(64, journal.listPending(scope).size)
        assertArrayEquals(JournalFixture.candidate().bytes, requireNotNull(journal.read(JournalFixture.identifier(0), scope)).candidate.bytes)
    }

    @Test
    fun `symlink hardlink insecure permissions and unknown files fail without repair`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val record = Files.readAllBytes(preparedPath())
        val outside = parent.resolve("outside")
        Files.write(outside, record)
        Files.delete(preparedPath())
        Files.createSymbolicLink(preparedPath(), outside)
        fails { journal().read(operation, scope) }
        Files.delete(preparedPath())
        Files.createLink(preparedPath(), outside)
        Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rw-------"))
        fails { journal().read(operation, scope) }
        Files.delete(preparedPath())
        Files.write(preparedPath(), record)
        Files.setPosixFilePermissions(preparedPath(), PosixFilePermissions.fromString("rw-r--r--"))
        fails { journal().read(operation, scope) }
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(preparedPath()))
        Files.setPosixFilePermissions(preparedPath(), PosixFilePermissions.fromString("rw-------"))
        val unknown = root.resolve("unexpected")
        Files.write(unknown, byteArrayOf(1))
        Files.setPosixFilePermissions(unknown, PosixFilePermissions.fromString("rw-------"))
        fails { journal().listPending(scope) }
        assertTrue(Files.exists(unknown))
        assertArrayEquals(record, Files.readAllBytes(outside))
    }

    @Test
    fun `directory substitution orphan marker invalid identity and invalid scope reject`() {
        fails { journal().persistPrepared("../escape", JournalFixture.candidate(), scope) }
        assertFalse(Files.exists(root))
        fails { scope.copy(ownerSubject = "google:subject") }
        fails { scope.copy(storageAccountBinding = "not-a-digest") }
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val real = parent.resolve("real")
        Files.move(root, real)
        Files.createSymbolicLink(root, real)
        fails { journal().read(operation, scope) }
        Files.delete(root)
        Files.move(real, root)
        Files.write(attemptPath(), byteArrayOf(1))
        Files.setPosixFilePermissions(attemptPath(), PosixFilePermissions.fromString("rw-------"))
        Files.delete(preparedPath())
        fails { journal().listPending(scope) }
    }

    @Test
    fun `separate processes cannot both admit the same durable operation`() {
        journal().persistPrepared(operation, JournalFixture.candidate(), scope)
        val first = child("attempt")
        val second = child("attempt")
        assertEquals(listOf(0, 17), listOf(finish(first), finish(second)).sorted())
        assertTrue(requireNotNull(journal().read(operation, scope)).createAttemptRecorded)
        fails { journal().markCreateAttempt(operation, scope) }
    }

    @Test
    fun `separate process crashes retain partial candidates and never invent a replacement`() {
        for (point in JournalDurabilityPoint.entries.filter { it.name.startsWith("PREPARED") }) {
            root.toFile().deleteRecursively()
            assertEquals(23, finish(child("prepare", point)))
            assertTrue(Files.exists(preparedPath()))
            if (point == JournalDurabilityPoint.PREPARED_CREATED || point == JournalDurabilityPoint.PREPARED_PARTIAL) {
                fails { journal().read(operation, scope) }
                fails { journal().persistPrepared(operation, JournalFixture.candidate(), scope) }
            } else {
                assertArrayEquals(GenerationFixture.bytes, requireNotNull(journal().read(operation, scope)).candidate.bytes)
                assertTrue(journal().markCreateAttempt(operation, scope).createAttemptRecorded)
            }
        }
    }

    @Test
    fun `separate process crashes at every marker stage prevent a second create admission`() {
        for (point in JournalDurabilityPoint.entries.filter { it.name.startsWith("ATTEMPT") }) {
            root.toFile().deleteRecursively()
            journal().persistPrepared(operation, JournalFixture.candidate(), scope)
            assertEquals(23, finish(child("attempt", point)))
            val before = Files.readAllBytes(attemptPath())
            fails { journal().markCreateAttempt(operation, scope) }
            assertArrayEquals(before, Files.readAllBytes(attemptPath()))
            assertArrayEquals(GenerationFixture.bytes, PasskeyBackupJournalRecord.decode(Files.readAllBytes(preparedPath()), operation).candidate.bytes)
        }
    }

    private fun journal(durability: PasskeyBackupJournalDurability = HostJournalDurability()) =
        PasskeyBackupGenerationJournal(root, durability)
    private fun preparedPath(): Path = root.resolve(operation + PasskeyBackupJournalDisk.PREPARED_SUFFIX)
    private fun attemptPath(): Path = root.resolve(operation + PasskeyBackupJournalDisk.ATTEMPT_SUFFIX)
    private fun commitPath(): Path = root.resolve(operation + PasskeyBackupJournalDisk.COMMIT_SUFFIX)
    private fun fails(action: () -> Unit) = assertTrue(runCatching(action).isFailure)

    private fun child(mode: String, point: JournalDurabilityPoint? = null): Process {
        val classpath = requireNotNull(System.getProperty("fearless.backup.journal.testClasspath")) {
            "Gradle must supply the complete child-process test classpath"
        }
        return ProcessBuilder(
            System.getProperty("java.home") + "/bin/java", "-cp", classpath,
            JournalChild::class.java.name, root.toString(), operation, mode, point?.name.orEmpty()
        )
            .redirectErrorStream(true).redirectOutput(parent.resolve("child-${System.nanoTime()}.log").toFile()).start()
    }

    private fun finish(process: Process): Int {
        assertTrue("Child did not finish", process.waitFor(20, TimeUnit.SECONDS))
        return process.exitValue()
    }
}

internal class HostJournalDurability(
    private val observe: (JournalDurabilityPoint) -> Unit = {
    }
) : PasskeyBackupJournalDurability {
    override fun syncDirectory(path: Path) { FileChannel.open(path, READ, NOFOLLOW_LINKS).use { it.force(true) } }
    override fun checkpoint(point: JournalDurabilityPoint) = observe(point)
}

internal object JournalFixture {
    val scope = GenerationFixture.context.let {
        PasskeyBackupJournalEntry.Scope(it.ownerSubject, it.backupNamespace, it.storageAccountBinding)
    }
    fun identifier(index: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(32) { index.toByte() }
    )
    fun candidate(
        index: Int = 0,
        fileId: String = "drive-id-$index"
    ): GoogleDrivePasskeyBackupGenerationStorage.Candidate {
        val original = GenerationFixture.generation()
        val context = if (index == 0) original.context else original.context.copy(generationId = identifier(index))
        val generation = PasskeyBackupGeneration(context, original.envelope, original.wrappers)
        return GoogleDrivePasskeyBackupGenerationStorage.Candidate(fileId, context, PasskeyBackupGenerationFormat.encode(generation))
    }
}

internal object JournalChild {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val durability = HostJournalDurability { if (it.name == arguments[3]) Runtime.getRuntime().halt(23) }
        val journal = PasskeyBackupGenerationJournal(Path.of(arguments[0]), durability)
        try {
            if (arguments[2] == "prepare") {
                journal.persistPrepared(arguments[1], JournalFixture.candidate(), JournalFixture.scope)
            } else if (arguments[2] == "commit") {
                journal.markCommitAttempt(arguments[1], JournalFixture.scope)
            } else {
                journal.markCreateAttempt(arguments[1], JournalFixture.scope)
            }
            exitProcess(0)
        } catch (_: Exception) {
            exitProcess(17)
        }
    }
}
