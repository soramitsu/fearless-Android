package jp.co.soramitsu.backup.passkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

/** Runs in the isolated library test APK; no real account, cloud request or wallet material is used. */
@RunWith(AndroidJUnit4::class)
class PasskeyBackupGenerationJournalDeviceTest {
    @After
    fun removeOnlyIsolatedTestApplicationJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.noBackupFilesDir.resolve("passkey-generations-v1").deleteRecursively()
    }

    @Test
    fun factoryUsesPrivateNoBackupDirectoryAndNativeDirectorySync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = "ERERERERERERERERERERERERERERERERERERERERERE"
        val scope = PasskeyBackupJournalEntry.Scope("owner:$token", "backup:$token", "aa".repeat(DIGEST_BYTES))
        val journal = PasskeyBackupGenerationJournal.forApplication(context)
        assertNull(journal.read(token, scope))
        val directory = context.noBackupFilesDir.toPath().resolve("passkey-generations-v1")
        assertTrue(Files.isDirectory(directory, NOFOLLOW_LINKS))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory, NOFOLLOW_LINKS))
        val lock = directory.resolve(".lock")
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(lock, NOFOLLOW_LINKS))
        assertEquals(0L, Files.size(lock))
        assertTrue(journal.listPending(scope).isEmpty())
    }

    @Test
    fun nativePersistReloadAndAttemptPreserveExactVectorAndDenyReplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vector = instrumentation.context.assets.open("passkey-generation-v1.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val expected = vector["context"].asJsonObject.let {
            PasskeyBackupGeneration.Context(
                it["ownerSubject"].asString, it["backupNamespace"].asString, it["generationId"].asString,
                it["parentHeadRevision"].asString.toLong(), it["parentHeadSha256"].asString,
                it["keyEpoch"].asString.toLong(), it["storageAccountBinding"].asString
            )
        }
        val bytes = Base64.getUrlDecoder().decode(vector["encodedBase64Url"].asString)
        assertEquals(vector["encodedBytes"].asInt, bytes.size)
        assertEquals(vector["sha256"].asString, PasskeyBackupGenerationFormat.sha256(bytes))
        val scope = PasskeyBackupJournalEntry.Scope(expected.ownerSubject, expected.backupNamespace, expected.storageAccountBinding)
        val operation = expected.generationId
        val candidate = GoogleDrivePasskeyBackupGenerationStorage.Candidate("synthetic-native-file-id", expected, bytes)
        val journal = PasskeyBackupGenerationJournal.forApplication(instrumentation.targetContext)
        assertFalse(journal.persistPrepared(operation, candidate, scope).createAttemptRecorded)
        val reopened = PasskeyBackupGenerationJournal.forApplication(instrumentation.targetContext)
        assertArrayEquals(bytes, requireNotNull(reopened.read(operation, scope)).candidate.bytes)
        assertTrue(reopened.markCreateAttempt(operation, scope).createAttemptRecorded)
        val afterAttempt = PasskeyBackupGenerationJournal.forApplication(instrumentation.targetContext)
        assertTrue(requireNotNull(afterAttempt.read(operation, scope)).createAttemptRecorded)
        assertTrue(runCatching { afterAttempt.markCreateAttempt(operation, scope) }.isFailure)
        assertArrayEquals(bytes, afterAttempt.listPending(scope).single().candidate.bytes)
    }
    private companion object { const val DIGEST_BYTES = 32 }
}
