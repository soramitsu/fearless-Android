package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeFamily
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeOutcome
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeRequest
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentResult
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
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

/** Adapter/authority ordering with a fake storage boundary; no SQL or transport claims. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointAttachmentsTest {
    private val dispatcher = StandardTestDispatcher()
    private val access = SavedEndpointAccess { 0L }
    private val owner = Any()
    private val ref = EndpointRecordRef(EndpointInventorySource.LEGACY, 7L)
    private var preparedCount = 0
    private var observedCount = 0
    private var committedCount = 0
    private var probedCount = 0
    private var prepareGate: suspend () -> Unit = {}
    private var commitGate: suspend () -> Unit = {}
    private var probeWork: suspend (EndpointProbeRequest) -> EndpointProbeOutcome = { EndpointProbeOutcome.MATCH }
    private var capture = capture()
    private val stored = mockk<EndpointAttachmentResult>()
    private val storage = object : SavedEndpointAttachments.Storage {
        override suspend fun prepare(operation: SavedEndpointAttachmentOperation): EndpointAttachmentCapture {
            assertSame(owner, operation.owner)
            assertEquals(ref, operation.ref)
            assertEquals(19L, operation.decisionId)
            assertEquals(3L, operation.decisionRevision)
            assertEquals(EndpointAttachmentAction.ADD_NEW, operation.action)
            preparedCount++
            prepareGate()
            return capture
        }
        override suspend fun observe(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult {
            assertSame(this@SavedEndpointAttachmentsTest.capture, capture)
            observedCount++
            return stored
        }
        override suspend fun commit(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult {
            assertSame(this@SavedEndpointAttachmentsTest.capture, capture)
            committedCount++
            commitGate()
            return stored
        }
    }
    private val adapter = SavedEndpointAttachments(access, storage, EndpointMutationLane()) { request -> probedCount++; probeWork(request) }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { access.revokeAll(); Dispatchers.resetMain() }

    @Test fun foreignAndUnclaimedLeasesCannotCreateOperations() {
        assertNull(adapter.newOperation(owner, SavedEndpointAccess.Lease(), ref, 19, 3, "chain", EndpointAttachmentAction.ADD_NEW))
        val lease = claim()
        assertNull(adapter.newOperation(Any(), lease, ref, 19, 3, "chain", EndpointAttachmentAction.ADD_NEW))
        assertEquals(0, preparedCount + observedCount + committedCount + probedCount)
    }

    @Test fun creationPerformsNoIoAndExactMatchRunsOneCommitAndOnePublication() = runTest(dispatcher) {
        val operation = operation()
        assertEquals(0, preparedCount + probedCount)
        var publications = 0
        probeWork = { assertEquals(EndpointProbeFamily.SUBSTRATE, it.family); EndpointProbeOutcome.MATCH }
        repeat(2) {
            adapter.execute(operation) {
                assertSame(stored, (it.getOrThrow() as SavedEndpointAttachmentOutcome.Stored).result)
                publications++
            }
        }
        assertEquals(1, publications)
        assertEquals(1, preparedCount)
        assertEquals(1, probedCount)
        assertEquals(1, committedCount)
        assertEquals(0, observedCount)
    }

    @Test fun alreadyPresentIsOnlyReobservedWithoutProbeOrCommit() = runTest(dispatcher) {
        capture = capture(present = true)
        adapter.execute(operation()) { assertSame(stored, (it.getOrThrow() as SavedEndpointAttachmentOutcome.Stored).result) }
        assertEquals(1, observedCount)
        assertEquals(0, probedCount + committedCount)
    }

    @Test fun everyNonMatchReportsItsReasonWithoutCommit() = runTest(dispatcher) {
        EndpointProbeOutcome.entries.filter { it != EndpointProbeOutcome.MATCH }.forEach { outcome ->
            probeWork = { outcome }
            adapter.execute(operation()) {
                assertEquals(outcome, (it.getOrThrow() as SavedEndpointAttachmentOutcome.Rejected).reason)
            }
        }
        assertEquals(EndpointProbeOutcome.entries.size - 1, probedCount)
        assertEquals(0, observedCount + committedCount)
        assertThrows(IllegalArgumentException::class.java) { SavedEndpointAttachmentOutcome.Rejected(EndpointProbeOutcome.MATCH) }
    }

    @Test fun unsupportedTargetOrInvalidExactUrlFailsBeforeProbe() = runTest(dispatcher) {
        listOf(capture(family = EndpointDecisionTarget.Family.UNSUPPORTED), capture(url = "wss://example.invalid")).forEach {
            capture = it
            var failed = false
            adapter.execute(operation()) { result -> failed = result.isFailure }
            assertTrue(failed)
        }
        assertEquals(0, probedCount + committedCount)
    }

    @Test fun cancelledBeforeExecuteDoesNoWorkOrPublication() = runTest(dispatcher) {
        val operation = operation()
        operation.cancel()
        adapter.execute(operation) { fail("retired operation published") }
        assertEquals(0, preparedCount + probedCount + committedCount)
    }

    @Test fun duplicateExecutionWhileFirstIsBlockedDoesNotRetireFirst() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val operation = operation()
        var publications = 0
        val first = launch { adapter.execute(operation) { publications++ } }
        runCurrent()
        adapter.execute(operation) { fail("duplicate published") }
        gate.complete(Unit)
        first.join()
        assertEquals(1, publications)
        assertEquals(1, preparedCount)
        assertEquals(1, probedCount)
    }

    @Test fun synchronousOperationCancelDuringPreparePreventsAttemptAndProbe() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val operation = operation()
        val job = launch { adapter.execute(operation) { fail("retired operation published") } }
        runCurrent()
        operation.cancel()
        gate.complete(Unit)
        job.join()
        assertEquals(0, probedCount + committedCount)
        assertThrows(IllegalStateException::class.java) { operation.bind(capture) }
    }

    @Test fun synchronousLeaseRevokeDuringPreparePreventsAttemptAndProbe() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val operation = operation()
        val job = launch { adapter.execute(operation) { fail("retired lease published") } }
        runCurrent()
        access.revokeAll()
        assertFalse(operation.authority.isCurrent())
        gate.complete(Unit)
        job.join()
        assertEquals(0, probedCount + committedCount)
    }

    @Test fun cancelDuringGrantedProbePreventsCommitAndPublicationWithoutWaitingForProbe() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        probeWork = { gate.await(); EndpointProbeOutcome.MATCH }
        val operation = operation()
        val job = launch { adapter.execute(operation) { fail("retired operation published") } }
        runCurrent()
        assertEquals(1, probedCount)
        operation.cancel()
        assertFalse(job.isCompleted)
        gate.complete(Unit)
        job.join()
        assertEquals(0, committedCount)
    }

    @Test fun replacementLeaseDuringProbeCannotReceiveOldSuccessOrFailure() = runTest(dispatcher) {
        for (failure in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            probeWork = { gate.await(); if (failure) error("synthetic") else EndpointProbeOutcome.MATCH }
            val operation = operation()
            val job = launch { adapter.execute(operation) { fail("old lease published") } }
            runCurrent()
            val fresh = claim()
            gate.complete(Unit)
            job.join()
            assertTrue(access.isCurrent(owner, fresh))
        }
        assertEquals(0, committedCount)
    }

    @Test fun cancellationDuringStorageCompletionSuppressesLatePublication() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        commitGate = { gate.await() }
        val operation = operation()
        val job = launch { adapter.execute(operation) { fail("retired operation published") } }
        runCurrent()
        assertEquals(1, committedCount)
        operation.cancel()
        gate.complete(Unit)
        job.join()
    }

    @Test fun currentFailurePublishesOnceAndJobCancellationRetiresTheOperation() = runTest(dispatcher) {
        val sentinel = IllegalStateException("synthetic")
        prepareGate = { throw sentinel }
        var count = 0
        adapter.execute(operation()) { assertSame(sentinel, it.exceptionOrNull()); count++ }
        assertEquals(1, count)
        val gate = CompletableDeferred<Unit>()
        prepareGate = { gate.await() }
        val operation = operation()
        val job = launch { adapter.execute(operation) { fail("cancelled job published") } }
        runCurrent()
        job.cancelAndJoin()
        assertFalse(operation.begin())
        assertThrows(IllegalStateException::class.java) { operation.bind(capture) }
        assertEquals(0, probedCount)
    }

    private fun operation(): SavedEndpointAttachmentOperation =
        checkNotNull(adapter.newOperation(owner, claim(), ref, 19, 3, "chain", EndpointAttachmentAction.ADD_NEW))

    private fun claim(): SavedEndpointAccess.Lease {
        val request = access.begin(owner)
        check(access.approve(owner, request.id))
        return checkNotNull(access.claim(owner, request.id))
    }

    private fun capture(
        present: Boolean = false,
        family: EndpointDecisionTarget.Family = EndpointDecisionTarget.Family.SUBSTRATE,
        url: String = "wss://example.invalid/"
    ): EndpointAttachmentCapture {
        val target = mockk<EndpointDecisionTarget> {
            every { chainId } returns "chain"
            every { this@mockk.family } returns family
            every { expectedGenesis } returns if (family == EndpointDecisionTarget.Family.UNSUPPORTED) null else "0x" + "11".repeat(32)
        }
        val decision = mockk<EndpointRecoveryDecision> {
            every { this@mockk.target } returns target
            every { candidate } returns EndpointDecisionCandidate("Explicit", url)
        }
        return mockk {
                // Fake storage returns an unbound capture; real ordered captures are covered by the SQL contract.
                every { this@mockk getProperty "mutation" } returns null
            every { alreadyPresent } returns present
            every { this@mockk.decision } returns decision
        }
    }
}
