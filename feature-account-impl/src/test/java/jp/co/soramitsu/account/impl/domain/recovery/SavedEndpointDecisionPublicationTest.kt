package jp.co.soramitsu.account.impl.domain.recovery

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Pure adapter publication ordering; these tests make no SQL rollback assertion. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointDecisionPublicationTest {
    private val dispatcher = StandardTestDispatcher()
    private val access = SavedEndpointAccess { 0L }
    private val owner = Any()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { access.revokeAll(); Dispatchers.resetMain() }

    @Test fun invalidLeaseRunsNeitherWorkNorPublication() = runTest(dispatcher) {
        var worked = false
        var published = false
        withDecisionLease(access, owner, SavedEndpointAccess.Lease(), { worked = true }, { published = true })
        assertFalse(worked); assertFalse(published)
    }

    @Test fun revokeDuringWorkSuppressesBothSuccessAndFailurePublication() = runTest(dispatcher) {
        for (failure in listOf(false, true)) {
            val lease = claim()
            val release = CompletableDeferred<Unit>()
            var published = false
            val job = launch {
                withDecisionLease(access, owner, lease, { authority ->
                    release.await()
                    assertFalse(authority.isCurrent())
                    if (failure) error("synthetic operation failure")
                    7
                }, { published = true })
            }
            runCurrent()
            access.revokeLease(owner)
            release.complete(Unit)
            job.join()
            assertFalse(published)
        }
    }

    @Test fun replacementLeaseDoesNotReceiveOldResult() = runTest(dispatcher) {
        val old = claim()
        val release = CompletableDeferred<Unit>()
        var published = false
        val job = launch { withDecisionLease(access, owner, old, { release.await(); 7 }, { published = true }) }
        runCurrent()
        val fresh = claim()
        release.complete(Unit)
        job.join()
        assertFalse(published)
        assertTrue(access.isCurrent(owner, fresh))
    }

    @Test fun currentResultAndFailurePublishSynchronouslyAndCancellationNeverPublishes() = runTest(dispatcher) {
        val lease = claim()
        var published = 0
        withDecisionLease(access, owner, lease, { 7 }, {
            assertEquals(7, it.getOrThrow())
            assertTrue(access.isCurrent(owner, lease))
            published++
        })
        assertEquals(1, published)
        val sentinel = IllegalStateException("synthetic")
        withDecisionLease<Int>(access, owner, lease, { throw sentinel }, {
            assertSame(sentinel, it.exceptionOrNull()); published++
        })
        val suspended = CompletableDeferred<Unit>()
        val job = launch { withDecisionLease(access, owner, lease, { suspended.await() }, { published++ }) }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(2, published)
    }

    private fun claim(): SavedEndpointAccess.Lease {
        val request = access.begin(owner)
        check(access.approve(owner, request.id))
        return checkNotNull(access.claim(owner, request.id))
    }
}
