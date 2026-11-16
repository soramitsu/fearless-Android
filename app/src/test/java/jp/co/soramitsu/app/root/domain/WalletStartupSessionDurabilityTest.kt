package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The startup session when secure storage loses durability while a check runs or after it finished. */
class WalletStartupSessionDurabilityTest {
    private data class Entry(val value: String) : WalletStartupPayload

    @Before
    fun setUp() {
        WalletSecureStorageHealthTestHooks.reset()
        WalletStartupSession.resetForTest()
    }

    @After
    fun tearDown() {
        WalletStartupSession.resetForTest()
        WalletSecureStorageHealthTestHooks.reset()
    }

    @Test
    fun aCheckThatReportsARestartLatchesTheWholeProcess(): Unit = runBlocking {
        val result = WalletStartupSession.openOrJoin { WalletDatabaseStartupResult.ProcessRestartRequired }.await()
        assertEquals(WalletDatabaseStartupResult.ProcessRestartRequired, result.result)
        assertFalse(WalletSecureStorageHealth.isHealthy())
        assertFalse(WalletStartupSession.isReady())
    }

    @Test
    fun aReadyResultAfterTheLatchIsNotTrusted(): Unit = runBlocking {
        val result = WalletStartupSession.openOrJoin {
            WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
            WalletDatabaseStartupResult.Ready
        }.await()
        assertEquals(WalletDatabaseStartupResult.ProcessRestartRequired, result.result)
        assertFalse(WalletStartupSession.isReady())
    }

    @Test
    fun theLatchCancelsARunningCheckAndAnswersItsWaiters(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val waiting = WalletStartupSession.openOrJoin {
            started.complete(Unit)
            awaitCancellation()
        }
        started.await()
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        assertEquals(WalletDatabaseStartupResult.ProcessRestartRequired, withTimeout(5_000) { waiting.await() }.result)
    }

    @Test
    fun aLateResultFromAnInvalidatedCheckIsDropped(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val waiting = WalletStartupSession.openOrJoin {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
            }
            finished.complete(Unit)
            WalletDatabaseStartupResult.Ready
        }
        started.await()
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        release.complete(Unit)
        withTimeout(5_000) { finished.await() }
        assertEquals(WalletDatabaseStartupResult.ProcessRestartRequired, waiting.await().result)
        assertFalse(WalletStartupSession.isReady())
    }

    @Test
    fun payloadsAreNotClaimedOnceStorageNeedsARestart(): Unit = runBlocking {
        WalletStartupSession.openOrJoin { WalletDatabaseStartupResult.Ready }.await()
        WalletStartupSession.recordPayload(Entry("deep link"))
        WalletSecureStorageHealthTestHooks.latchProcessRestartRequired()
        assertNull(WalletStartupSession.claimLatestPayload())
        assertFalse(WalletStartupSession.isReady())
    }

    @Test
    fun aRestoredOlderEntryIsNotForwardedAgain(): Unit = runBlocking {
        WalletStartupSession.openOrJoin { WalletDatabaseStartupResult.Ready }.await()
        val first = WalletStartupSession.recordPayload(Entry("first"))
        assertEquals(Entry("first"), WalletStartupSession.claimLatestPayload()?.payload)
        assertEquals(first, WalletStartupSession.recordPayload(Entry("first, restored"), restoredSequence = first))
        assertNull("Already forwarded", WalletStartupSession.claimLatestPayload())
    }

    @Test
    fun onlyTheCurrentFailedAttemptCanBePresented(): Unit = runBlocking {
        val failed = WalletStartupSession.openOrJoin { WalletDatabaseStartupResult.DatabaseOpenFailed }.await()
        val owner = Any()
        assertFalse(WalletStartupSession.claimFailurePresentation(failed.attemptId + 1, owner))
        assertTrue(WalletStartupSession.claimFailurePresentation(failed.attemptId, owner))
        assertTrue("The same owner keeps its lease", WalletStartupSession.claimFailurePresentation(failed.attemptId, owner))
        assertFalse(WalletStartupSession.claimFailurePresentation(failed.attemptId, Any()))
    }
}
