package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredPairing
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSession
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSessionNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ReownSessionImporterTest {

    private val reown = section("reown")
    private val now = reown["now"].asLong
    private val source = ReownFixtureSource()
    private val attempts = MemoryImportAttempts()
    private val storage = WcMemoryStorage()
    private val logs = CopyOnWriteArrayList<String>()

    private fun importer(from: ReownLegacySource = source) =
        ReownSessionImporter(source = from, attempts = attempts, nowSeconds = { now }, log = { logs += it })

    private fun storedState() = WcStoredStateJson.decodeOrEmpty(storage.json)

    @Test
    fun `the first start imports what the SDK kept and its mark in one write, and lets the cleanup run`() {
        val result = runBlocking { importer().importInto(storage) }

        val summary = (result as ReownImportResult.Imported).summary
        assertEquals(listOf(4, 1, 2), listOf(summary.sessions, summary.expiredSessions, summary.pairings))
        assertTrue(summary.identity)
        assertTrue(result.allowsCleanup)
        assertEquals(1, storage.writes)
        val state = storedState()
        assertEquals(4, state.sessions.size)
        assertEquals(false, ReownImportMarker.read(state))
    }

    @Test
    fun `an app killed after the import was saved does not import again, and the cleanup still runs`() {
        runBlocking { importer().importInto(storage) }
        val saved = storage.json
        val reads = source.keyChainReads

        // The app dies here, before LegacyWalletConnectSdkCleanup ran; the SDK's files are all still there.
        val again = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.AlreadyImported(keepsLegacyFiles = false), again)
        assertTrue(again.allowsCleanup)
        assertEquals(saved, storage.json)
        assertEquals(1, storage.writes)
        assertEquals(reads, source.keyChainReads)
    }

    @Test
    fun `an app killed before the import was saved imports again without duplicates or mixed-up keys`() {
        // What a first run may have left without its mark: the same sessions, one of them with a key of the client's own.
        val (partial, _) = ReownLegacyStateImport.merge(WcStoredState(), snapshotOf(source), now)
        val own = partial.sessions.first().copy(symKey = "11".repeat(32))
        storage.json = WcStoredStateJson.encode(partial.copy(sessions = listOf(own) + partial.sessions.drop(1)))

        val result = runBlocking { importer().importInto(storage) }

        assertTrue(result is ReownImportResult.Imported)
        val state = storedState()
        assertEquals(partial.sessions.map { it.topic }, state.sessions.map { it.topic })
        assertEquals(own, state.sessions.first())
        assertEquals(false, ReownImportMarker.read(state))
    }

    @Test
    fun `a device the SDK never ran on has nothing to import and nothing is written`() {
        source.hasData = false

        val result = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.NothingToImport, result)
        assertTrue(result.allowsCleanup)
        assertNull(storage.json)
        assertEquals(0, attempts.value)
    }

    @Test
    fun `unreadable SDK data fails without writing, keeps the cleanup off, and stops after three attempts`() {
        source.failure = GeneralSecurityException("keystore says no")

        repeat(ReownSessionImporter.MAX_ATTEMPTS) {
            val result = runBlocking { importer().importInto(storage) }
            assertEquals(ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE), result)
            assertFalse(result.allowsCleanup)
        }
        source.failure = null
        val reads = source.keyChainReads
        val final = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.GAVE_UP), final)
        assertFalse(final.allowsCleanup)
        assertEquals(reads, source.keyChainReads)
        assertNull(storage.json)
        assertTrue(source.hasLegacyData())
    }

    @Test
    fun `an import that crashes the app still uses up an attempt, so it cannot become a crash loop`() {
        source.failure = OutOfMemoryError("simulated crash while reading")

        repeat(ReownSessionImporter.MAX_ATTEMPTS) {
            assertThrows(OutOfMemoryError::class.java) { importer().importBlocking(storage) }
        }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.GAVE_UP), importer().importBlocking(storage))
        assertEquals(ReownSessionImporter.MAX_ATTEMPTS, source.keyChainReads)
    }

    @Test
    fun `databases whose key is gone, damaged or wrong are never guessed at`() {
        val cutShort = LegacyDatabaseFiles(bytes("reown-sign.db").copyOf(5000))
        val cases = listOf<ReownFixtureSource.() -> Unit>(
            { passphrase = { null } },
            { passphrase = { ByteArray(32) { 7 } } },
            { databases = databases + (ReownLegacySnapshotReader.SIGN_DATABASE to cutShort) },
            { failure = IOException("disk") }
        )
        cases.forEach { breakIt ->
            val broken = ReownFixtureSource().apply(breakIt)
            val result = runBlocking { ReownSessionImporter(broken, MemoryImportAttempts(), { now }).importInto(storage) }
            assertEquals(ReownImportResult.Failed(ReownImportFailure.LEGACY_UNREADABLE), result)
        }
        assertNull(storage.json)
    }

    @Test
    fun `client storage that cannot be read now is retried later without using up an attempt`() {
        storage.unreadable = true

        val result = runBlocking { importer().importInto(storage) }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE), result)
        assertFalse(result.allowsCleanup)
        assertEquals(0, attempts.value)
        assertEquals(0, source.keyChainReads)

        storage.unreadable = false
        assertTrue(runBlocking { importer().importInto(storage) } is ReownImportResult.Imported)
    }

    @Test
    fun `a save that fails gives the attempt back and saves nothing`() {
        val failing = object : WalletConnectStateStorage {
            override fun read(): String? = null

            override fun write(json: String) = error("WalletConnect state could not be stored")

            override fun clear() = Unit
        }

        val result = runBlocking { importer().importInto(failing) }

        assertEquals(ReownImportResult.Failed(ReownImportFailure.STORAGE_UNAVAILABLE), result)
        assertEquals(0, attempts.value)
    }

    @Test
    fun `a save that does not read back with everything is not counted as done, and a later start imports again`() {
        val forgetful = object : WalletConnectStateStorage {
            override fun read(): String? = null

            override fun write(json: String) = Unit // accepted, but never kept

            override fun clear() = Unit
        }
        val lossy = object : WalletConnectStateStorage {
            var json: String? = null

            override fun read(): String? = json

            override fun write(json: String) {
                val state = WcStoredStateJson.decodeOrEmpty(json)
                this.json = WcStoredStateJson.encode(state.copy(sessions = state.sessions.drop(1)))
            }

            override fun clear() = Unit
        }

        listOf(forgetful, lossy).forEach { broken ->
            val result = runBlocking { importer().importInto(broken) }
            assertEquals(ReownImportResult.Failed(ReownImportFailure.NOT_SAVED), result)
            assertFalse(result.allowsCleanup)
        }

        // No mark may stay behind without everything it vouches for, or a later start would allow the cleanup.
        assertNull(ReownImportMarker.read(WcStoredStateJson.decodeOrEmpty(lossy.json)))
        assertEquals(2, attempts.value)
        assertTrue(runBlocking { importer().importInto(storage) } is ReownImportResult.Imported)
    }

    @Test
    fun `a caller that stops waiting is released at once, and the import then writes nothing`() {
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val hung = ReownFixtureSource(beforeRead = { release.await() })
        val importer = ReownSessionImporter(hung, attempts, { now }, log = { line ->
            logs += line
            done.countDown()
        })

        val started = System.nanoTime()
        val result = runBlocking { withTimeoutOrNull(200) { importer.importInto(storage) } }
        val waitedMillis = (System.nanoTime() - started) / 1_000_000
        release.countDown()

        assertNull(result)
        assertTrue("the caller waited $waitedMillis ms", waitedMillis < 10_000)
        assertTrue(done.await(60, TimeUnit.SECONDS))
        assertTrue(logs.single(), logs.single().startsWith("Reown import failed: CANCELLED"))
        assertNull(storage.json)
        assertEquals(0, storage.writes)
        assertTrue(hung.hasLegacyData())
    }

    @Test
    fun `closing the gate stops writes that have not started and waits for one under way`() {
        val gate = ReownImportWriteGate()
        val writing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val writer = thread {
            gate.ifOpen {
                writing.countDown()
                finish.await()
                "written"
            }
        }
        assertTrue(writing.await(10, TimeUnit.SECONDS))

        thread {
            gate.close()
            closed.countDown()
        }
        assertFalse(closed.await(300, TimeUnit.MILLISECONDS))
        finish.countDown()
        assertTrue(closed.await(10, TimeUnit.SECONDS))
        writer.join()

        assertNull(gate.ifOpen { "too late" })
    }

    @Test
    fun `closing the gate is never held longer than its wait by a write that hangs`() {
        val gate = ReownImportWriteGate(closeWaitMillis = 100)
        val writing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        thread(isDaemon = true) {
            gate.ifOpen {
                writing.countDown()
                finish.await()
                "written"
            }
        }
        assertTrue(writing.await(10, TimeUnit.SECONDS))

        val started = System.nanoTime()
        gate.close()
        val heldMillis = (System.nanoTime() - started) / 1_000_000
        finish.countDown()

        assertTrue("close() was held $heldMillis ms", heldMillis < 5_000)
    }

    @Test
    fun `what the client already stored is kept beside what comes over`() {
        val own = WcStoredPairing("ab".repeat(32), "cd".repeat(32), "irn", now + 600, false, WcStoredMetadata("Own", "", "", emptyList()))
        storage.json = WcStoredStateJson.encode(WcStoredState(clientSeed = "ef".repeat(32), pairings = listOf(own)))

        runBlocking { importer().importInto(storage) }

        val state = storedState()
        assertEquals(own, state.pairings.first())
        assertEquals(3, state.pairings.size)
        assertEquals("ef".repeat(32), state.clientSeed)
        assertEquals(4, state.sessions.size)
    }

    @Test
    fun `an import that had to leave a live session out never lets the cleanup delete the SDK's files`() {
        val full = (1 until WcLimits.MAX_SESSIONS).map { index ->
            WcStoredSession(
                topic = index.toString().padStart(64, '0'),
                symKey = "33".repeat(32),
                selfPublicKey = "44".repeat(32),
                peerPublicKey = "55".repeat(32),
                pairingTopic = "",
                relayProtocol = "irn",
                expiry = now + 100,
                acknowledged = true,
                namespaces = mapOf("eip155" to WcStoredSessionNamespace(null, emptyList(), emptyList(), emptyList())),
                peer = WcStoredMetadata("", "", "", emptyList())
            )
        }
        storage.json = WcStoredStateJson.encode(WcStoredState(sessions = full))

        val result = runBlocking { importer().importInto(storage) }

        assertTrue(result is ReownImportResult.Imported)
        assertFalse(result.allowsCleanup)
        assertEquals(true, ReownImportMarker.read(storedState()))
        val later = runBlocking { importer().importInto(storage) }
        assertEquals(ReownImportResult.AlreadyImported(keepsLegacyFiles = true), later)
        assertFalse(later.allowsCleanup)
    }

    @Test
    fun `the database passphrase is wiped once used`() {
        runBlocking { importer().importInto(storage) }

        assertTrue(source.handedOutPassphrases.isNotEmpty())
        assertTrue(source.handedOutPassphrases.all { key -> key.all { it.toInt() == 0 } })
    }

    @Test
    fun `every outcome is logged as counts and codes, never a topic or key`() {
        runBlocking { importer().importInto(storage) }
        runBlocking { importer().importInto(storage) }
        source.failure = GeneralSecurityException("keystore says no")
        runBlocking { importer().importInto(WcMemoryStorage()) }

        assertTrue(logs[0], logs[0].startsWith("Reown import: 4 sessions (1 expired), 2 pairings, identity true"))
        assertTrue(logs[1], logs[1].startsWith("Reown import: done earlier"))
        assertTrue(logs[2], logs[2].startsWith("Reown import failed: LEGACY_UNREADABLE"))
        val secrets = ReownFixtureSource.fixtureKeyChain()
            .flatMap { (tag, value) -> listOf(tag, value) }
            .filter { it.length >= 32 }
        assertTrue(logs.none { line -> secrets.any { it.take(16) in line } || "keystore" in line })
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

    private fun snapshotOf(fixture: ReownFixtureSource): ReownLegacySnapshot {
        val decoder = SqlCipherDatabaseDecoder()
        val key = requireNotNull(fixture.passphrase())
        return ReownLegacySnapshotReader().read(
            ReownLegacySources(
                keyChain = fixture.keyChain,
                signDatabase = SqliteDatabaseImage(decoder.decode(fixture.databases.getValue(ReownLegacySnapshotReader.SIGN_DATABASE), key)),
                coreDatabase = SqliteDatabaseImage(decoder.decode(fixture.databases.getValue(ReownLegacySnapshotReader.CORE_DATABASE), key))
            )
        )
    }
}
