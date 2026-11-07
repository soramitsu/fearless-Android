package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredPairing
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

class ReownSessionImporterTest {

    private val now = section("reown")["now"].asLong
    private val source = ReownFixtureSource()
    private val ledger = MemoryImportLedger()
    private val storage = WcMemoryStorage()

    private fun importer() = ReownSessionImporter(source = source, ledger = ledger, nowSeconds = { now })

    @Test
    fun `the first start imports what the SDK kept and lets the cleanup run`() {
        val result = runBlocking { importer().importInto(storage) }

        val summary = (result as ReownImportResult.Imported).summary
        assertEquals(listOf(2, 1, 1), listOf(summary.sessions, summary.pairings, summary.requests))
        assertTrue(summary.identity)
        assertTrue(result.allowsCleanup)
        assertTrue(ledger.isDone)
        val state = WcStoredStateJson.decodeOrEmpty(storage.json)
        assertEquals(2, state.sessions.size)
        assertEquals(1, storage.writes)
    }

    @Test
    fun `later starts do nothing`() {
        runBlocking { importer().importInto(storage) }
        val stored = storage.json

        val again = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.AlreadyImported, again)
        assertTrue(again.allowsCleanup)
        assertEquals(stored, storage.json)
        assertEquals(1, storage.writes)
    }

    @Test
    fun `a device the SDK never ran on has nothing to import and nothing is written`() {
        source.hasData = false

        val result = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.NothingToImport, result)
        assertTrue(result.allowsCleanup)
        assertTrue(ledger.isDone)
        assertNull(storage.json)
    }

    @Test
    fun `unreadable SDK data fails, keeps the cleanup off, and gives up after three attempts`() {
        source.failure = GeneralSecurityException("keystore says no")

        repeat(ReownSessionImporter.MAX_FAILED_ATTEMPTS) {
            val result = runBlocking { importer().importInto(storage) }
            assertEquals(ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE), result)
            assertFalse(result.allowsCleanup)
        }
        source.failure = null
        val final = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.GAVE_UP), final)
        assertFalse(final.allowsCleanup)
        assertFalse(ledger.isDone)
        assertNull(storage.json)
    }

    @Test
    fun `databases whose key is gone, damaged or wrong are never guessed at`() {
        val cases = listOf<ReownFixtureSource.() -> Unit>(
            { passphrase = { null } },
            { passphrase = { ByteArray(32) { 7 } } },
            { databases = databases + (ReownLegacySnapshotReader.SIGN_DATABASE to LegacyDatabaseFiles(bytes("reown-sign.db").copyOf(5000))) },
            { failure = IOException("disk") }
        )
        cases.forEach { breakIt ->
            val broken = ReownFixtureSource().apply(breakIt)
            val result = runBlocking { ReownSessionImporter(broken, MemoryImportLedger(), { now }).importInto(storage) }
            assertEquals(ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE), result)
        }
        assertNull(storage.json)
    }

    @Test
    fun `client storage that cannot be used now is retried without using up an attempt`() {
        storage.unreadable = true

        val result = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE), result)
        assertFalse(result.allowsCleanup)
        assertEquals(0, ledger.failedAttempts)
        assertFalse(ledger.isDone)

        storage.unreadable = false
        assertTrue(runBlocking { importer().importInto(storage) } is ReownImportResult.Imported)
    }

    @Test
    fun `what the client already stored is kept beside what comes over`() {
        val own = WcStoredPairing("ab".repeat(32), "cd".repeat(32), "irn", now + 600, false, WcStoredMetadata("Own", "", "", emptyList()))
        storage.json = WcStoredStateJson.encode(WcStoredState(clientSeed = "ef".repeat(32), pairings = listOf(own)))

        runBlocking { importer().importInto(storage) }

        val state = WcStoredStateJson.decodeOrEmpty(storage.json)
        assertEquals(own, state.pairings.first())
        assertEquals(2, state.pairings.size)
        assertEquals("ef".repeat(32), state.clientSeed)
        assertEquals(2, state.sessions.size)
    }

    @Test
    fun `the database passphrase is wiped once used`() {
        runBlocking { importer().importInto(storage) }

        assertTrue(source.handedOutPassphrases.isNotEmpty())
        assertTrue(source.handedOutPassphrases.all { key -> key.all { it.toInt() == 0 } })
    }

    @Test
    fun `the stored database key unwraps from the SDK's layout and a bad layout is refused`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(48) { 9 }
        val wrapped = byteArrayOf(0, 0, 0, 12) + iv + ciphertext

        val seen = mutableListOf<Pair<ByteArray, ByteArray>>()
        val opened = unwrapDatabasePassphrase(wrapped) { givenIv, givenCiphertext ->
            seen += givenIv to givenCiphertext
            ByteArray(32) { 1 }
        }

        assertTrue(opened.all { it.toInt() == 1 })
        assertTrue(seen.single().first.contentEquals(iv))
        assertTrue(seen.single().second.contentEquals(ciphertext))
        listOf(byteArrayOf(0, 0), byteArrayOf(0, 0, 0, 99) + iv, byteArrayOf(0, 0, 0, 12) + iv).forEach { bad ->
            assertThrows(LegacyDatabaseException::class.java) { unwrapDatabasePassphrase(bad) { _, _ -> ByteArray(0) } }
        }
    }
}
