package jp.co.soramitsu.walletconnect.impl.data

import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport.Outcome
import jp.co.soramitsu.walletconnect.impl.presentation.WCDelegate
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClientConfig
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestClock
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestRelay
import jp.co.soramitsu.walletconnect.impl.protocol.eventually
import jp.co.soramitsu.walletconnect.impl.protocol.storedSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class WalletConnectStarterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = WcTestClock()
    private val storage = RecordingStorage()
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Volatile
    private var cleanups = 0
    private val logs = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `start attaches the client once and follows the process lifecycle`() {
        val client = client()
        val starter = starter(client, LegacyWalletConnectImport.NotAttempted)

        starter.start()
        starter.start()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        awaitStartup(starter)
        eventually { "client read" in storage.log }

        assertSame(client, WCDelegate.client)
        assertEquals(1, owner.registry.observerCount)
        assertEquals(1, storage.log.count { it == "client read" })
    }

    @Test
    fun `the import runs off the main thread before the client loads the state it wrote`() {
        val client = client()
        val callerThread = Thread.currentThread()
        var importThread: Thread? = null
        val importer = importer { target ->
            importThread = Thread.currentThread()
            target.write(sessionState(IMPORTED_TOPIC))
            storage.log += "imported"
            Outcome.IMPORTED
        }
        val starter = starter(client, importer)

        starter.start()
        awaitStartup(starter)
        // The client loads its state in the background once started.
        eventually { client.sessions.value.isNotEmpty() }

        assertNotEquals(callerThread, importThread)
        assertEquals(listOf("write", "imported", "client read"), storage.log.take(3))
        assertEquals(listOf(IMPORTED_TOPIC), client.sessions.value.map { it.topic })
    }

    @Test
    fun `the import receives the storage the client loads`() {
        var received: WalletConnectStateStorage? = null
        val starter = starter(
            client(),
            importer { target ->
                received = target
                Outcome.NOTHING_TO_IMPORT
            }
        )

        starter.start()
        awaitStartup(starter)

        assertSame(storage, received)
    }

    @Test
    fun `the old SDK's files are deleted only after an import or when there was nothing to import`() {
        val expected = mapOf(
            Outcome.IMPORTED to 1,
            Outcome.NOTHING_TO_IMPORT to 1,
            Outcome.FAILED to 0,
            Outcome.NOT_ATTEMPTED to 0
        )
        expected.forEach { (outcome, runs) ->
            cleanups = 0
            val starter = starter(client(), importer { outcome })

            starter.start()
            awaitStartup(starter)

            assertEquals(outcome.name, runs, cleanups)
        }
    }

    @Test
    fun `without an importer nothing is imported and the old SDK's files are kept`() {
        val starter = starter(client(), LegacyWalletConnectImport.NotAttempted)

        starter.start()
        awaitStartup(starter)
        eventually { "client read" in storage.log }

        assertEquals(0, cleanups)
        assertEquals(listOf("client read"), storage.log)
        assertEquals(Outcome.NOT_ATTEMPTED, runBlocking { LegacyWalletConnectImport.NotAttempted.importInto(storage) })
    }

    @Test
    fun `each start logs the import outcome by name and nothing else`() {
        val starter = starter(client(), importer { error("legacy database at /data/wc.db unreadable") })

        starter.start()
        starter.start()
        awaitStartup(starter)

        assertEquals(listOf("Legacy WalletConnect import: FAILED"), logs)
    }

    @Test
    fun `an import that throws counts as failed and the client still starts`() {
        storage.json = sessionState(STORED_TOPIC)
        val client = client()
        val starter = starter(client, importer { error("unreadable legacy database") })

        starter.start()
        awaitStartup(starter)
        eventually { client.sessions.value.isNotEmpty() }

        assertEquals(0, cleanups)
        assertEquals(listOf(STORED_TOPIC), client.sessions.value.map { it.topic })
    }

    @Test
    fun `an import that never finishes is given up and the client still starts`() {
        val client = client()
        val starter = starter(client, importer { awaitCancellation() }, importTimeoutMillis = 100)

        starter.start()
        awaitStartup(starter)
        eventually { "client read" in storage.log }

        assertEquals(0, cleanups)
    }

    private fun awaitStartup(starter: WalletConnectStarter) {
        runBlocking { withTimeout(10_000) { starter.awaitStartup() } }
    }

    private fun client() = WalletConnectClient(
        storage = storage,
        transport = WcTestRelay(),
        verifyKeySource = { error("offline") },
        config = WalletConnectClientConfig(
            "0123456789abcdef0123456789abcdef",
            null,
            WalletConnectMetadata("w", "w", "https://w", emptyList()),
            emptySet(),
            "ua"
        ),
        clock = clock,
        scope = scope
    )

    private fun starter(
        client: WalletConnectClient,
        legacyImport: LegacyWalletConnectImport,
        importTimeoutMillis: Long = 10_000
    ) = WalletConnectStarter(
        client = client,
        storage = storage,
        legacyImport = legacyImport,
        legacyCleanup = countingCleanup(),
        processLifecycle = { owner.registry },
        scope = scope,
        importTimeoutMillis = importTimeoutMillis,
        log = { logs += it }
    )

    private fun importer(block: suspend (WalletConnectStateStorage) -> Outcome) = object : LegacyWalletConnectImport {
        override suspend fun importInto(storage: WalletConnectStateStorage): Outcome = block(storage)
    }

    // Counts the cleanup's runs through the marker it reads first; reports it as already done.
    private fun countingCleanup() = LegacyWalletConnectSdkCleanup(
        markers = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } answers {
                cleanups += 1
                true
            }
        },
        deleteDatabaseFiles = {},
        deleteSharedPreferences = {},
        keyStore = mockk()
    )

    private fun sessionState(topic: String): String =
        storedSessionState(topic, expiry = clock.nowSeconds() + ONE_DAY_SECONDS)

    /** Storage in memory that records who read and wrote it, in order. */
    private class RecordingStorage : WalletConnectStateStorage {
        val log = CopyOnWriteArrayList<String>()

        @Volatile
        var json: String? = null

        override fun read(): String? {
            log += "client read"
            return json
        }

        override fun write(json: String) {
            log += "write"
            this.json = json
        }

        override fun clear() {
            log += "clear"
            json = null
        }
    }

    private companion object {
        const val ONE_DAY_SECONDS = 86_400L
        val IMPORTED_TOPIC = "aa".repeat(32)
        val STORED_TOPIC = "bb".repeat(32)
    }
}
