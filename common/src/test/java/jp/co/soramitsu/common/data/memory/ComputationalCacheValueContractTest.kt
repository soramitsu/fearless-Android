package jp.co.soramitsu.common.data.memory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.util.concurrent.atomic.AtomicInteger

class ComputationalCacheValueContractTest {
    @get:Rule val main: TestRule = CacheMainRule()

    @Test fun successfulValueIsReusedForTheSameKeyAndLifecycle() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        val calls = AtomicInteger()
        try {
            assertEquals(
                "first",
                cache.useCache("validators", owner) {
                    calls.incrementAndGet()
                    "first"
                }
            )
            assertEquals(
                "first",
                cache.useCache("validators", owner) {
                    calls.incrementAndGet()
                    "second"
                }
            )
            assertEquals(1, calls.get())
        } finally {
            owner.destroy()
        }
    }

    @Test fun nullableResultsAreCachedWithoutRecomputing() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        val calls = AtomicInteger()
        try {
            val first = cache.useCache<String?>("missing", owner) {
                calls.incrementAndGet()
                null
            }
            val second = cache.useCache<String?>("missing", owner) {
                calls.incrementAndGet()
                "invented"
            }
            assertEquals(null, first)
            assertEquals(null, second)
            assertEquals(1, calls.get())
        } finally {
            owner.destroy()
        }
    }

    @Test fun differentKeysKeepIndependentValues() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        try {
            assertEquals("alice", cache.useCache("wallet-a", owner) { "alice" })
            assertEquals("bob", cache.useCache("wallet-b", owner) { "bob" })
            assertEquals("alice", cache.useCache("wallet-a", owner) { "wrong" })
        } finally {
            owner.destroy()
        }
    }

    @Test fun failedComputationCanBeRetriedByTheSameOwner() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        val failure = IllegalStateException("Catalogue temporarily unavailable")
        try {
            val first = runCatching { cache.useCache<String>("validators", owner) { throw failure } }
            assertTrue(generateSequence(first.exceptionOrNull()) { it.cause }.any { it === failure })
            assertEquals("recovered", cache.useCache("validators", owner) { "recovered" })
        } finally {
            owner.destroy()
        }
    }

    @Test fun cancelledComputationDoesNotPoisonTheNextLoad() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        try {
            val first = runCatching {
                cache.useCache<String>("validators", owner) { throw CancellationException("Source changed") }
            }
            assertTrue(first.exceptionOrNull() is CancellationException)
            assertEquals("fresh", cache.useCache("validators", owner) { "fresh" })
        } finally {
            owner.destroy()
        }
    }

    @Test fun destroyedOwnerCannotStartNewWork() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        val calls = AtomicInteger()
        owner.destroy()
        val result = runCatching { cache.useCache("validators", owner) { calls.incrementAndGet() } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(0, calls.get())
        assertEquals(0, owner.observerCount.value)
    }

    @Test fun repeatedCacheHitsDoNotAccumulateLifecycleObservers() = runTest {
        val cache = ComputationalCache()
        val owner = CacheLifecycle()
        try {
            repeat(8) { assertEquals("value", cache.useCache("validators", owner) { "value" }) }
            assertEquals(1, owner.observerCount.value)
        } finally {
            owner.destroy()
        }
        assertEquals(0, owner.observerCount.value)
    }

    @Test fun destroyingOneOwnerKeepsTheValueUntilTheLastOwnerLeaves() = runTest {
        val cache = ComputationalCache()
        val first = CacheLifecycle()
        val second = CacheLifecycle()
        val replacement = CacheLifecycle()
        try {
            assertEquals("original", cache.useCache("validators", first) { "original" })
            assertEquals("original", cache.useCache("validators", second) { "wrong" })
            first.destroy()
            assertEquals("original", cache.useCache("validators", second) { "wrong" })
            second.destroy()
            assertEquals("replacement", cache.useCache("validators", replacement) { "replacement" })
        } finally {
            first.destroy()
            second.destroy()
            replacement.destroy()
        }
    }
}
