package jp.co.soramitsu.app.root.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealthTestHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletStartupSessionTest {

    private data class TestPayload(val value: String) : WalletStartupPayload

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
    fun `two concurrent entries share exactly one startup open`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<WalletDatabaseStartupResult>()

        WalletStartupSession.recordPayload(TestPayload("launcher"))
        val first = WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
        }
        started.await()
        val second = WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.DatabaseOpenFailed
        }

        assertSame(first, second)
        assertEquals(1, calls.get())

        release.complete(WalletDatabaseStartupResult.Ready)
        assertEquals(WalletDatabaseStartupResult.Ready, first.await().result)
        assertEquals(WalletDatabaseStartupResult.Ready, second.await().result)
        assertEquals(1, calls.get())
        assertTrue(WalletStartupSession.isReady())
    }

    @Test
    fun `deep link arriving during pending gate replaces launcher payload`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()

            WalletStartupSession.recordPayload(TestPayload("launcher"))
            val result = WalletStartupSession.openOrJoin {
                started.complete(Unit)
                release.await()
            }
            started.await()
            WalletStartupSession.recordPayload(TestPayload("deep-link"))
            release.complete(WalletDatabaseStartupResult.Ready)
            result.await()

            assertEquals(
                "deep-link",
                claimedPayload().value
            )
        }

    @Test
    fun `launcher and deep-link ordering always forwards newest entry`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("deep-link-old"))
            WalletStartupSession.recordPayload(TestPayload("launcher-new"))
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals("launcher-new", claimedPayload().value)

            WalletStartupSession.recordPayload(TestPayload("launcher-old"))
            WalletStartupSession.recordPayload(TestPayload("deep-link-new"))

            assertEquals("deep-link-new", claimedPayload().value)
        }

    @Test
    fun `failure retry keeps newest payload and starts one new attempt`() =
        runBlocking {
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("launcher"))

            val failed = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()

            assertFalse(WalletStartupSession.isReady())
            val failureOwner = Any()
            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    failed.attemptId,
                    failureOwner
                )
            )
            assertFalse(
                WalletStartupSession.claimFailurePresentation(
                    failed.attemptId,
                    Any()
                )
            )

            WalletStartupSession.recordPayload(TestPayload("retry-deep-link"))
            val retainedFailure = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }
            assertSame(failed, retainedFailure.await())
            assertEquals(1, calls.get())

            val retried = WalletStartupSession.retry {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals(WalletDatabaseStartupResult.Ready, retried.result)
            assertEquals(2, calls.get())
            assertEquals("retry-deep-link", claimedPayload().value)
        }

    @Test
    fun `rotation restores payload and joins process-owned open`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<WalletDatabaseStartupResult>()
        val sequence = WalletStartupSession.recordPayload(
            TestPayload("before-rotation")
        )
        val first = WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
        }
        started.await()

        val restoredSequence = WalletStartupSession.recordPayload(
            payload = TestPayload("restored-copy"),
            restoredSequence = sequence
        )
        val restored = WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.DatabaseOpenFailed
        }

        assertEquals(sequence, restoredSequence)
        assertSame(first, restored)
        assertEquals(1, calls.get())

        release.complete(WalletDatabaseStartupResult.Ready)
        restored.await()
        assertEquals("restored-copy", claimedPayload().value)
    }

    @Test
    fun `process restoration re-admits saved payload after session reset`() =
        runBlocking {
            val sequence = WalletStartupSession.recordPayload(
                TestPayload("before-process-death")
            )
            WalletStartupSession.resetForTest()

            assertEquals(
                sequence,
                WalletStartupSession.recordPayload(
                    payload = TestPayload("restored-after-process-death"),
                    restoredSequence = sequence
                )
            )
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals(
                "restored-after-process-death",
                claimedPayload().value
            )
        }

    @Test
    fun `ready process forwards new entry without reopening gate`() = runBlocking {
        val calls = AtomicInteger()
        WalletStartupSession.recordPayload(TestPayload("initial"))
        WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.Ready
        }.await()
        assertEquals("initial", claimedPayload().value)

        WalletStartupSession.recordPayload(TestPayload("ready-deep-link"))
        val fastPath = WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.DatabaseOpenFailed
        }.await()

        assertEquals(WalletDatabaseStartupResult.Ready, fastPath.result)
        assertEquals(1, calls.get())
        assertEquals("ready-deep-link", claimedPayload().value)
    }

    @Test
    fun `ready process becomes restart-required after ambiguous durability`() =
        runBlocking {
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("initial"))
            WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()
            assertEquals("initial", claimedPayload().value)
            assertTrue(WalletStartupSession.isReady())

            WalletSecureStorageHealthTestHooks
                .latchProcessRestartRequired()
            WalletStartupSession.recordPayload(TestPayload("blocked-deep-link"))
            val blocked = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()
            val retry = WalletStartupSession.retry {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals(
                WalletDatabaseStartupResult.ProcessRestartRequired,
                blocked.result
            )
            assertSame(blocked, retry)
            assertEquals(1, calls.get())
            assertFalse(WalletStartupSession.isReady())
            assertTrue(WalletStartupSession.claimLatestPayload() == null)
        }

    @Test
    fun `durability latch wins over an in-flight ready completion`() =
        runBlocking {
            val calls = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()
            WalletStartupSession.recordPayload(TestPayload("pending"))
            val pending = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
            }
            started.await()

            WalletSecureStorageHealthTestHooks
                .latchProcessRestartRequired()
            release.complete(WalletDatabaseStartupResult.Ready)

            assertEquals(
                WalletDatabaseStartupResult.ProcessRestartRequired,
                pending.await().result
            )
            assertEquals(1, calls.get())
            assertFalse(WalletStartupSession.isReady())
            assertTrue(WalletStartupSession.claimLatestPayload() == null)
        }

    @Test
    fun `concurrent entries after latch all join one restart failure`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("initial"))
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()
            claimedPayload()
            WalletSecureStorageHealthTestHooks
                .latchProcessRestartRequired()
            val calls = AtomicInteger()

            val blocked = coroutineScope {
                (0 until 128).map { index ->
                    async(Dispatchers.Default) {
                        WalletStartupSession.recordPayload(
                            TestPayload("blocked-$index")
                        )
                        WalletStartupSession.openOrJoin {
                            calls.incrementAndGet()
                            WalletDatabaseStartupResult.Ready
                        }
                    }
                }.awaitAll()
            }

            assertTrue(blocked.all { it === blocked.first() })
            blocked.forEach {
                assertEquals(
                    WalletDatabaseStartupResult.ProcessRestartRequired,
                    it.await().result
                )
            }
            assertEquals(0, calls.get())
            assertFalse(WalletStartupSession.isReady())
        }

    @Test
    fun `fresh process session recovers from process-local latch`() =
        runBlocking {
            WalletSecureStorageHealthTestHooks
                .latchProcessRestartRequired()
            WalletStartupSession.recordPayload(TestPayload("blocked"))
            val blocked = WalletStartupSession.openOrJoin {
                error("latched process must not run the gate")
            }.await()
            assertEquals(
                WalletDatabaseStartupResult.ProcessRestartRequired,
                blocked.result
            )

            WalletSecureStorageHealthTestHooks.reset()
            WalletStartupSession.resetForTest()
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("fresh-launcher"))
            val fresh = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals(WalletDatabaseStartupResult.Ready, fresh.result)
            assertEquals(1, calls.get())
            assertTrue(WalletStartupSession.isReady())
            assertEquals("fresh-launcher", claimedPayload().value)
        }

    @Test
    fun `one payload can launch heavy root only once`() = runBlocking {
        WalletStartupSession.recordPayload(TestPayload("single"))
        WalletStartupSession.openOrJoin {
            WalletDatabaseStartupResult.Ready
        }.await()

        assertEquals("single", claimedPayload().value)
        assertTrue(WalletStartupSession.claimLatestPayload() == null)
    }

    @Test
    fun `restoration cannot resurrect a payload already handed off`() =
        runBlocking {
            val forwardedSequence = WalletStartupSession.recordPayload(
                TestPayload("forwarded")
            )
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()
            assertEquals("forwarded", claimedPayload().value)

            WalletStartupSession.recordPayload(
                payload = TestPayload("stale-restoration"),
                restoredSequence = forwardedSequence
            )
            assertTrue(WalletStartupSession.claimLatestPayload() == null)

            WalletStartupSession.recordPayload(TestPayload("fresh-entry"))
            assertEquals("fresh-entry", claimedPayload().value)
        }

    @Test
    fun `older restored activity cannot overwrite a newer deep link`() =
        runBlocking {
            val oldSequence = WalletStartupSession.recordPayload(
                TestPayload("old")
            )
            WalletStartupSession.recordPayload(TestPayload("new-deep-link"))

            val staleActivitySequence = WalletStartupSession.recordPayload(
                payload = TestPayload("stale-restoration"),
                restoredSequence = oldSequence
            )
            WalletStartupSession.recordPayload(
                payload = TestPayload("stale-second-restoration"),
                restoredSequence = staleActivitySequence
            )
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()

            assertEquals("new-deep-link", claimedPayload().value)
        }

    @Test
    fun `unexpected startup exception is fail closed and retryable`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("payload"))
            val failed = WalletStartupSession.openOrJoin {
                error("injected startup failure")
            }.await()

            assertEquals(
                WalletDatabaseStartupResult.DatabaseOpenFailed,
                failed.result
            )
            assertFalse(WalletStartupSession.isReady())

            val retainedFailure = WalletStartupSession.openOrJoin {
                error("retained failures must not run a new check")
            }.await()
            assertSame(failed, retainedFailure)

            val retry = WalletStartupSession.retry {
                WalletDatabaseStartupResult.Ready
            }.await()
            assertEquals(WalletDatabaseStartupResult.Ready, retry.result)
            assertTrue(WalletStartupSession.isReady())
        }

    @Test
    fun `failure presentation lease transfers only after owner release`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("payload"))
            val failed = WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()
            val destroyedActivity = Any()
            val recreatedActivity = Any()

            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    failed.attemptId,
                    destroyedActivity
                )
            )
            assertFalse(
                WalletStartupSession.claimFailurePresentation(
                    failed.attemptId,
                    recreatedActivity
                )
            )

            WalletStartupSession.releaseFailurePresentation(
                failed.attemptId,
                destroyedActivity
            )

            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    failed.attemptId,
                    recreatedActivity
                )
            )
        }

    @Test
    fun `two concurrent explicit retries start exactly one new open`() =
        runBlocking {
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("payload"))
            WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()

            val firstRetry = WalletStartupSession.retry {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
            }
            started.await()
            val secondRetry = WalletStartupSession.retry {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }

            assertSame(firstRetry, secondRetry)
            assertEquals(2, calls.get())

            release.complete(WalletDatabaseStartupResult.Ready)
            assertEquals(
                WalletDatabaseStartupResult.Ready,
                firstRetry.await().result
            )
            assertEquals(
                WalletDatabaseStartupResult.Ready,
                secondRetry.await().result
            )
            assertEquals(2, calls.get())
        }

    @Test
    fun `dependency cancellation fails closed instead of stranding splash`() =
        runBlocking {
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("payload"))

            val failed = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                throw java.util.concurrent.CancellationException(
                    "injected dependency cancellation"
                )
            }.await()

            assertEquals(
                WalletDatabaseStartupResult.DatabaseOpenFailed,
                failed.result
            )
            assertFalse(WalletStartupSession.isReady())
            assertEquals(1, calls.get())

            val retained = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()
            assertSame(failed, retained)
            assertEquals(1, calls.get())

            val retry = WalletStartupSession.retry {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.Ready
            }.await()
            assertEquals(WalletDatabaseStartupResult.Ready, retry.result)
            assertTrue(WalletStartupSession.isReady())
            assertEquals(2, calls.get())
        }

    @Test
    fun `cancelled activity waiter cannot cancel process owned startup`() =
        runBlocking {
            val calls = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()
            WalletStartupSession.recordPayload(TestPayload("payload"))

            val shared = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
            }
            started.await()

            val waiterStarted = CompletableDeferred<Unit>()
            val activityWaiter = async {
                waiterStarted.complete(Unit)
                shared.await()
            }
            waiterStarted.await()
            activityWaiter.cancelAndJoin()

            val recreatedActivity = WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }
            assertSame(shared, recreatedActivity)
            assertEquals(1, calls.get())

            release.complete(WalletDatabaseStartupResult.Ready)
            assertEquals(
                WalletDatabaseStartupResult.Ready,
                recreatedActivity.await().result
            )
            assertTrue(WalletStartupSession.isReady())
            assertEquals("payload", claimedPayload().value)
        }

    @Test
    fun `many concurrent entries share one open and newest sequence wins`() =
        runBlocking {
            val calls = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()

            val entries = coroutineScope {
                (0 until 128).map { index ->
                    async(Dispatchers.Default) {
                        val payload = TestPayload("payload-$index")
                        val sequence =
                            WalletStartupSession.recordPayload(payload)
                        val result = WalletStartupSession.openOrJoin {
                            calls.incrementAndGet()
                            started.complete(Unit)
                            release.await()
                        }
                        Triple(sequence, payload, result)
                    }
                }.awaitAll()
            }
            started.await()

            assertEquals(1, calls.get())
            release.complete(WalletDatabaseStartupResult.Ready)
            entries.forEach { (_, _, result) ->
                assertEquals(
                    WalletDatabaseStartupResult.Ready,
                    result.await().result
                )
            }

            val expected = entries.maxBy { it.first }.second
            assertEquals(expected, claimedPayload())
            assertTrue(WalletStartupSession.claimLatestPayload() == null)
        }

    @Test
    fun `retry after ready never reopens startup dependencies`() = runBlocking {
        val calls = AtomicInteger()
        WalletStartupSession.recordPayload(TestPayload("initial"))
        WalletStartupSession.openOrJoin {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.Ready
        }.await()
        assertEquals("initial", claimedPayload().value)

        WalletStartupSession.recordPayload(TestPayload("after-ready"))
        val retry = WalletStartupSession.retry {
            calls.incrementAndGet()
            WalletDatabaseStartupResult.DatabaseOpenFailed
        }.await()

        assertEquals(WalletDatabaseStartupResult.Ready, retry.result)
        assertEquals(1, calls.get())
        assertEquals("after-ready", claimedPayload().value)
    }

    @Test
    fun `stale failure owner cannot release a newer failure lease`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("payload"))
            val firstFailure = WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()
            val staleOwner = Any()
            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    firstFailure.attemptId,
                    staleOwner
                )
            )

            val secondFailure = WalletStartupSession.retry {
                WalletDatabaseStartupResult.SecureStorageUnavailable
            }.await()
            val currentOwner = Any()
            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    secondFailure.attemptId,
                    currentOwner
                )
            )

            WalletStartupSession.releaseFailurePresentation(
                firstFailure.attemptId,
                staleOwner
            )

            assertFalse(
                WalletStartupSession.claimFailurePresentation(
                    secondFailure.attemptId,
                    Any()
                )
            )
            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    secondFailure.attemptId,
                    currentOwner
                )
            )
        }

    @Test
    fun `immediate startup failures cannot race their own registration`() =
        runBlocking {
            repeat(256) { iteration ->
                WalletStartupSession.resetForTest()
                WalletStartupSession.recordPayload(
                    TestPayload("payload-$iteration")
                )

                val first = WalletStartupSession.openOrJoin {
                    WalletDatabaseStartupResult.DatabaseOpenFailed
                }.await()
                assertEquals(
                    WalletDatabaseStartupResult.DatabaseOpenFailed,
                    first.result
                )

                val retry = WalletStartupSession.retry {
                    WalletDatabaseStartupResult.SecureStorageUnavailable
                }.await()
                assertEquals(
                    WalletDatabaseStartupResult.SecureStorageUnavailable,
                    retry.result
                )
                assertTrue(retry.attemptId > first.attemptId)
            }
        }

    @Test
    fun `cancelled attempt unwinding after reset cannot replace new phase`() =
        runBlocking {
            val staleStarted = CompletableDeferred<Unit>()
            val staleRelease =
                CompletableDeferred<WalletDatabaseStartupResult>()
            WalletStartupSession.recordPayload(TestPayload("stale"))
            val stale = WalletStartupSession.openOrJoin {
                staleStarted.complete(Unit)
                staleRelease.await()
            }
            staleStarted.await()

            WalletStartupSession.resetForTest()
            WalletStartupSession.recordPayload(TestPayload("current"))
            val currentStarted = CompletableDeferred<Unit>()
            val currentRelease =
                CompletableDeferred<WalletDatabaseStartupResult>()
            val current = WalletStartupSession.openOrJoin {
                currentStarted.complete(Unit)
                currentRelease.await()
            }
            currentStarted.await()

            staleRelease.complete(WalletDatabaseStartupResult.Ready)
            currentRelease.complete(WalletDatabaseStartupResult.Ready)

            assertTrue(stale.isCancelled)
            assertEquals(
                WalletDatabaseStartupResult.Ready,
                current.await().result
            )
            assertTrue(WalletStartupSession.isReady())
            assertEquals("current", claimedPayload().value)
        }

    @Test
    fun `many concurrent retries join exactly one replacement attempt`() =
        runBlocking {
            val calls = AtomicInteger()
            WalletStartupSession.recordPayload(TestPayload("payload"))
            WalletStartupSession.openOrJoin {
                calls.incrementAndGet()
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()
            val replacementStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<WalletDatabaseStartupResult>()

            val retries = coroutineScope {
                (0 until 128).map {
                    async(Dispatchers.Default) {
                        WalletStartupSession.retry {
                            calls.incrementAndGet()
                            replacementStarted.complete(Unit)
                            release.await()
                        }
                    }
                }.awaitAll()
            }
            replacementStarted.await()

            assertEquals(2, calls.get())
            assertTrue(retries.all { it === retries.first() })

            release.complete(WalletDatabaseStartupResult.Ready)
            retries.forEach { retry ->
                assertEquals(
                    WalletDatabaseStartupResult.Ready,
                    retry.await().result
                )
            }
            assertEquals(2, calls.get())
        }

    @Test
    fun `concurrent payload claims allow exactly one wallet handoff`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("single-handoff"))
            WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.Ready
            }.await()

            val claims = coroutineScope {
                (0 until 128).map {
                    async(Dispatchers.Default) {
                        WalletStartupSession.claimLatestPayload()
                    }
                }.awaitAll()
            }

            assertEquals(1, claims.count { it != null })
            assertEquals(
                TestPayload("single-handoff"),
                claims.single { it != null }?.payload
            )
            assertTrue(WalletStartupSession.claimLatestPayload() == null)
        }

    @Test
    fun `concurrent failure claims allow exactly one error presenter`() =
        runBlocking {
            WalletStartupSession.recordPayload(TestPayload("payload"))
            val failure = WalletStartupSession.openOrJoin {
                WalletDatabaseStartupResult.DatabaseOpenFailed
            }.await()
            val owners = List(128) { Any() }

            val claims = coroutineScope {
                owners.map { owner ->
                    async(Dispatchers.Default) {
                        owner to WalletStartupSession.claimFailurePresentation(
                            failure.attemptId,
                            owner
                        )
                    }
                }.awaitAll()
            }

            assertEquals(1, claims.count { it.second })
            val winner = claims.single { it.second }.first
            assertTrue(
                WalletStartupSession.claimFailurePresentation(
                    failure.attemptId,
                    winner
                )
            )
            assertFalse(
                WalletStartupSession.claimFailurePresentation(
                    failure.attemptId,
                    Any()
                )
            )
        }

    private fun claimedPayload(): TestPayload {
        val claim = requireNotNull(WalletStartupSession.claimLatestPayload())
        return claim.payload as TestPayload
    }
}
