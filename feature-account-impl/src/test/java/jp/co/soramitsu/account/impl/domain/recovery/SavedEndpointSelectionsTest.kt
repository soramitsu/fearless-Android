package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionOwner
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionResult
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Actual lease/selection authority and final-grant ordering; fake storage, no SQL claim. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointSelectionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val access = SavedEndpointAccess { 0L }
    private val owner = Any()
    private val lane = EndpointMutationLane()
    private val decision = SelectionCoreTestBridge.decision(
        1, 2,
        SelectionCoreTestBridge.sourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 4, 2), "a".repeat(64)),
        EndpointDecisionCandidate("Public fixture", "https://public.invalid/"),
        SelectionCoreTestBridge.target(
            "chain", "b".repeat(64), EndpointDecisionTarget.Family.SOLANA,
            "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
        )
    )
    private val request = EndpointSelectionRequest(decision, EndpointSelectionOwner(4, 2, 7))
    private var prepared = 0
    private var committed = 0
    private var prepareGate: suspend () -> Unit = {}
    private var afterGrant: suspend () -> Unit = {}
    private var failCommit = false
    private val store = object : SavedEndpointSelections.Storage {
        override suspend fun observe(decision: EndpointRecoveryDecision) =
            SelectionCoreTestBridge.readRuntime<EndpointSelectionRequest?>(lane, "chain") { request }
        override suspend fun prepare(operation: SavedEndpointSelectionOperation): EndpointSelectionCapture {
            prepared++
            prepareGate()
            return SelectionCoreTestBridge.capture(operation.request, operation.mutation)
        }
        override suspend fun commit(
            capture: EndpointSelectionCapture,
            authority: EndpointSelectionAuthority,
            attempt: EndpointSelectionAuthority.Attempt
        ): EndpointSelectionResult = lane.withOperation(SelectionCoreTestBridge.operation(capture)) {
            SelectionCoreTestBridge.commitOnce(authority, attempt, capture) { committed++ }
            afterGrant()
            if (failCommit) error("public fixture physical commit failed")
            SelectionCoreTestBridge.result(capture)
        }
    }
    private val adapter = SavedEndpointSelections(access, store, lane)

    @Before fun before() { Dispatchers.setMain(dispatcher) }

    @After fun after() {
        access.revokeAll()
    Dispatchers.resetMain()
    }
    private fun claim(): SavedEndpointAccess.Lease {
        val token = access.begin(owner)
        assertTrue(access.approve(owner, token.id))
        return checkNotNull(access.claim(owner, token.id))
    }
    private suspend fun operation(lease: SavedEndpointAccess.Lease) =
        checkNotNull(adapter.newOperation(owner, lease, store.observe(decision)))

    @Test fun unclaimedForeignAndRevokedLeaseCannotAllocateSelection() = runTest(dispatcher) {
        val observed = store.observe(decision)
        assertNull(adapter.newOperation(owner, SavedEndpointAccess.Lease(), observed))
        val lease = claim()
        assertNull(adapter.newOperation(Any(), lease, observed))
        access.revokeAll()
        assertNull(adapter.newOperation(owner, lease, observed))
        assertEquals(0, prepared + committed)
    }

    @Test fun newLeaseAndDifferentDatabaseNeverReuseSelectionAuthority() {
        val first = claim()
        val a = checkNotNull(access.selectionAuthority(owner, first, lane))
        assertSame(a, access.selectionAuthority(owner, first, lane))
        assertNull(access.selectionAuthority(owner, first, EndpointMutationLane()))
        val second = claim()
        assertFalse(a.isCurrent())
        val b = checkNotNull(access.selectionAuthority(owner, second, lane))
        assertNotSame(a, b)
        assertTrue(b.isCurrent())
        assertNull(access.selectionAuthority(owner, first, lane))
    }

    @Test fun creationPerformsNoStorageAndOneInvocationPublishesOnceAfterCommit() = runTest(dispatcher) {
        val op = operation(claim())
        assertEquals(0, prepared + committed)
        var calls = 0
        repeat(2) {
            adapter.execute(op) {
                assertEquals(1, committed)
            assertSame(decision, it.getOrThrow().decision)
            calls++
            }
        }
        assertEquals(1, calls)
        assertEquals(1, prepared)
        assertEquals(1, committed)
    }

    @Test fun staleObservationCannotAllocateNewIntent() = runTest(dispatcher) {
        val lease = claim()
        val observed = store.observe(decision)
        val latest = lane.admit(listOf("chain"))
        assertNull(adapter.newOperation(owner, lease, observed))
        assertTrue(lane.isCurrent(latest))
    }

    @Test fun lifecycleRetirementDuringHeldPreparationCannotCommitOrPublish() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val op = operation(claim())
        var calls = 0
        val job = launch { adapter.execute(op) { calls++ } }
        runCurrent()
        access.revokeAll()
        gate.complete(Unit)
        job.join()
        assertEquals(0, committed)
        assertEquals(0, calls)
    }

    @Test fun explicitOperationRetirementDuringPreparationCannotCommitOrPublish() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val op = operation(claim())
        var calls = 0
        val job = launch { adapter.execute(op) { calls++ } }
        runCurrent()
        op.cancel()
        gate.complete(Unit)
        job.join()
        assertEquals(0, committed)
        assertEquals(0, calls)
    }

    @Test fun newerSameChainIntentRetiresHeldSelectionButOtherChainDoesNot() = runTest(dispatcher) {
        for (id in listOf("other", "chain")) {
            val gate = CompletableDeferred<Unit>()
            prepareGate = { gate.await() }
            val before = committed
            var calls = 0
            val op = operation(claim())
            val job = launch { adapter.execute(op) { calls++ } }
            runCurrent()
            lane.admit(listOf(id))
            gate.complete(Unit)
            job.join()
            assertEquals(if (id == "other") 1 else 0, committed - before)
            assertEquals(if (id == "other") 1 else 0, calls)
        }
    }

    @Test fun physicalCommitFailurePublishesFailureAndCannotReplay() = runTest(dispatcher) {
        failCommit = true
        val op = operation(claim())
        var failures = 0
        repeat(2) {
            adapter.execute(op) {
            assertTrue(it.isFailure)
        failures++
        }
        }
        assertEquals(1, committed)
        assertEquals(1, failures)
    }

    @Test fun revocationAfterFinalGrantSuppressesPublicationWithoutPretendingRollback() = runTest(dispatcher) {
        afterGrant = { access.revokeAll() }
        var calls = 0
        adapter.execute(operation(claim())) { calls++ }
        assertEquals(1, committed)
        assertEquals(0, calls)
    }

    @Test fun cancellationRetiresOperationAndNeverPublishes() = runTest(dispatcher) {
        prepareGate = { CompletableDeferred<Unit>().await() }
        val op = operation(claim())
        var calls = 0
        val job = launch { adapter.execute(op) { calls++ } }
        runCurrent()
        job.cancelAndJoin()
        assertFalse(lane.isCurrent(op.mutation))
        assertEquals(0, committed)
        assertEquals(0, calls)
    }

    @Test fun readOnlyObservationRetiredBeforeMainPublicationDoesNotPublish() = runTest(dispatcher) {
        val lease = claim()
        var calls = 0
        access.revokeAll()
        adapter.observe(owner, lease, decision) { calls++ }
        assertEquals(0, calls)
        assertEquals(0, prepared + committed)
    }

    @Test fun cancellationNotifiesOutsideOperationMonitorBeforeAndAfterCaptureBinding() = runTest(dispatcher) {
        for (bound in listOf(false, true)) {
            val op = operation(claim())
            if (bound) {
                assertTrue(op.begin())
            op.bind(store.prepare(op))
            }
            val locked = mutableListOf<Boolean>()
            val observer = launch(Dispatchers.Unconfined) {
                lane.runtimeChanges.collect { locked += Thread.holdsLock(op) }
            }
            locked.clear()
            op.cancel()
            assertTrue("Cancellation must emit its runtime retirement", locked.isNotEmpty())
            assertTrue("Runtime observation cannot run under the selection operation monitor", locked.none { it })
            assertFalse(lane.isCurrent(op.mutation))
            assertFalse(op.begin())
            assertTrue(runCatching { op.bind(store.prepare(op)) }.isFailure)
            observer.cancelAndJoin()
        }
    }
}
