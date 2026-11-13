package jp.co.soramitsu.common.data.memory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.util.concurrent.atomic.AtomicInteger

class ComputationalCacheOwnershipContractTest {
    @get:Rule val main: TestRule = CacheMainRule()

    @Test fun concurrentOwnersShareOneComputation() = runTest {
        val cache = ComputationalCache()
        val owners = List(8) { CacheLifecycle() }
        val reply = CompletableDeferred<String>()
        val calls = AtomicInteger()
        val jobs = owners.map { owner ->
            async(Dispatchers.Default) {
                cache.useCache("validators", owner) {
                    calls.incrementAndGet()
                    reply.await()
                }
            }
        }
        try {
            owners.forEach { it.awaitOwner() }
            reply.complete("shared")
            assertEquals(List(8) { "shared" }, jobs.awaitAll())
            assertEquals(1, calls.get())
        } finally {
            reply.complete("cleanup")
            jobs.forEach { it.cancelAndJoin() }
            owners.forEach { it.destroy() }
        }
    }

    @Test fun cancellingTheFirstCallerDoesNotCancelAnotherOwnersComputation() = runTest {
        val cache = ComputationalCache()
        val firstOwner = CacheLifecycle()
        val secondOwner = CacheLifecycle()
        val reply = CompletableDeferred<String>()
        val calls = AtomicInteger()
        val first = async {
            cache.useCache("validators", firstOwner) {
                calls.incrementAndGet()
                reply.await()
            }
        }
        val second = async(start = CoroutineStart.LAZY) {
            cache.useCache("validators", secondOwner) {
                calls.incrementAndGet()
                reply.await()
            }
        }
        try {
            firstOwner.awaitOwner()
            second.start()
            secondOwner.awaitOwner()
            first.cancelAndJoin()
            reply.complete("still needed")
            assertEquals("still needed", second.await())
            assertEquals(1, calls.get())
        } finally {
            reply.complete("cleanup")
            first.cancelAndJoin()
            second.cancelAndJoin()
            firstOwner.destroy()
            secondOwner.destroy()
        }
    }

    @Test fun cancellingAWaitingCallerDoesNotInvalidateTheOriginalLoad() = runTest {
        val cache = ComputationalCache()
        val firstOwner = CacheLifecycle()
        val secondOwner = CacheLifecycle()
        val reply = CompletableDeferred<String>()
        val first = async { cache.useCache("validators", firstOwner) { reply.await() } }
        val second = async(start = CoroutineStart.LAZY) { cache.useCache("validators", secondOwner) { "wrong computation" } }
        try {
            firstOwner.awaitOwner()
            second.start()
            secondOwner.awaitOwner()
            second.cancelAndJoin()
            reply.complete("original")
            assertEquals("original", first.await())
            assertEquals("original", cache.useCache("validators", firstOwner) { "wrong replacement" })
        } finally {
            reply.complete("cleanup")
            first.cancelAndJoin()
            second.cancelAndJoin()
            firstOwner.destroy()
            secondOwner.destroy()
        }
    }

    @Test fun lastOwnerDestructionCancelsItsPendingComputation() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        val started = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val caller = async {
            runCatching {
                cache.useCache("validators", owner) {
                    started.complete(Unit)
                    try {
                        reply.await()
                    } catch (error: CancellationException) {
                        cancelled.complete(Unit)
                        throw error
                    }
                }
            }
        }
        try {
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            owner.destroy()
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
            assertTrue(caller.await().exceptionOrNull() is CancellationException)
            assertFalse(reply.isCompleted)
            assertEquals(0, owner.observerCount.value)
        } finally {
            reply.complete(Unit)
            caller.cancelAndJoin()
            owner.destroy()
        }
    }

    @Test fun oneOwnersDestructionCannotCancelWorkStillOwnedByAnotherScreen() = runTest {
        val cache = ComputationalCache()
        val firstOwner = CacheLifecycle()
        val secondOwner = CacheLifecycle()
        val reply = CompletableDeferred<String>()
        val first = async { cache.useCache("validators", firstOwner) { reply.await() } }
        val second = async(start = CoroutineStart.LAZY) { cache.useCache("validators", secondOwner) { "wrong computation" } }
        try {
            firstOwner.awaitOwner()
            second.start()
            secondOwner.awaitOwner()
            firstOwner.destroy()
            reply.complete("owned by second screen")
            assertEquals("owned by second screen", second.await())
            assertEquals("owned by second screen", first.await())
        } finally {
            reply.complete("cleanup")
            first.cancelAndJoin()
            second.cancelAndJoin()
            firstOwner.destroy()
            secondOwner.destroy()
        }
    }

    @Test fun anOldFailedComputationCannotEvictAReplacementForTheSameKey() = runTest {
        val cache = ComputationalCache()
        val oldOwner = CacheLifecycle()
        val newOwner = CacheLifecycle()
        val started = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val first = async {
            runCatching {
                cache.useCache<String>("validators", oldOwner) {
                    calls.incrementAndGet()
                    withContext(NonCancellable) {
                        started.complete(Unit)
                        releaseOld.await()
                    }
                    error("Obsolete failure")
                }
            }
        }
        try {
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            oldOwner.destroy()
            assertEquals(
                "replacement",
                cache.useCache("validators", newOwner) {
                    calls.incrementAndGet()
                    "replacement"
                }
            )
            releaseOld.complete(Unit)
            assertTrue(first.await().isFailure)
            assertEquals(
                "replacement",
                cache.useCache("validators", newOwner) {
                    calls.incrementAndGet()
                    "wrong"
                }
            )
            assertEquals(2, calls.get())
        } finally {
            releaseOld.complete(Unit)
            first.cancelAndJoin()
            oldOwner.destroy()
            newOwner.destroy()
        }
    }
}
