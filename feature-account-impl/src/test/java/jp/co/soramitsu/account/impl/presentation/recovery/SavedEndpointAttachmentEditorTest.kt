package jp.co.soramitsu.account.impl.presentation.recovery

import io.mockk.coVerify
import io.mockk.coEvery
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.impl.domain.recovery.*
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeOutcome
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real editor/adapter/lease coordination with mocked SQL and probe boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointAttachmentEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val access = SavedEndpointAccess { 0L }
    private val owner = Any()
    private var lease: SavedEndpointAccess.Lease? = null
    private val decisions = mockk<SavedEndpointDecisions>()
    private var ref = EndpointRecordRef(EndpointInventorySource.CURRENT, 7)
    private var state: SavedEndpointDecisionForm? = null
    private lateinit var editor: SavedEndpointDecisionEditor
    private lateinit var decision: EndpointRecoveryDecision
    private var persisted = true
    private var probeCount = 0
    private var commitCount = 0
    private var prepareCount = 0
    private var observedCount = 0
    private var lastAction: EndpointAttachmentAction? = null
    private var lastOperation: SavedEndpointAttachmentOperation? = null
    private var probeWork: suspend () -> EndpointProbeOutcome = { EndpointProbeOutcome.MATCH }
    private var prepareWork: suspend () -> Unit = {}
    private var present = false
    private var storedKind = EndpointAttachmentResult.Kind.ADDED_INACTIVE
    private var storedActive = false
    private var refresh = false
    private val storage = object : SavedEndpointAttachments.Storage {
        override suspend fun prepare(operation: SavedEndpointAttachmentOperation): EndpointAttachmentCapture {
            prepareCount++
            assertEquals(ref, operation.ref)
            assertEquals(19L, operation.decisionId)
            assertEquals(decision.revision, operation.decisionRevision)
            lastAction = operation.action
            lastOperation = operation
            prepareWork()
            return mockk {
                // Fake storage returns an unbound capture; real ordered captures are covered by the SQL contract.
                every { this@mockk getProperty "mutation" } returns null
                every { alreadyPresent } returns present
                every { this@mockk.decision } returns this@SavedEndpointAttachmentEditorTest.decision
            }
        }
        override suspend fun observe(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult { observedCount++; return result() }
        override suspend fun commit(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult { commitCount++; return result() }
    }
    private val attachments = SavedEndpointAttachments(access, storage, EndpointMutationLane()) { probeCount++; probeWork() }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val request = access.begin(owner)
        check(access.approve(owner, request.id))
        lease = checkNotNull(access.claim(owner, request.id))
    }
    @After fun cleanup() { access.revokeAll(); Dispatchers.resetMain() }

    @Test fun loadAndSaveNeverProbeAndOnlyExactSavedSnapshotCanAttach() = runTest(dispatcher) {
        open(this)
        assertTrue(state!!.attachmentReady)
        assertEquals(0, prepareCount + probeCount)
        editor.save(state!!); runCurrent()
        assertTrue(state!!.saved)
        assertEquals(0, prepareCount + probeCount)
        val exact = state!!
        editor.attach(exact.copy())
        assertEquals(0, prepareCount)
        editor.attach(exact); editor.attach(exact); runCurrent()
        assertEquals(1, probeCount)
        assertEquals(1, commitCount)
        assertEquals(EndpointAttachmentAction.ADD_NEW, lastAction)
        assertEquals(SavedEndpointAttachmentStatus.ADDED_INACTIVE, state!!.attachmentStatus)
        editor.clear()
    }

    @Test fun editsAndTargetChangesRequireAnotherExplicitSaveBeforeAttachment() = runTest(dispatcher) {
        open(this)
        val old = state!!
        editor.changeText(old.identity, name = "changed")
        assertFalse(state!!.attachmentReady)
        editor.attach(old); editor.attach(state!!); runCurrent()
        assertEquals(0, probeCount + prepareCount)
        editor.clear()
    }

    @Test fun unsavedInitialChoiceCannotAttach() = runTest(dispatcher) {
        persisted = false
        open(this)
        assertFalse(state!!.attachmentReady)
        editor.attach(state!!); runCurrent()
        assertEquals(0, prepareCount + probeCount)
        editor.clear()
    }

    @Test fun attachmentBlocksEditsSaveAndTargetPagingUntilItFinishes() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        probeWork = { gate.await(); EndpointProbeOutcome.MATCH }
        open(this)
        editor.attach(state!!); runCurrent()
        val busy = state!!
        assertTrue(busy.attaching)
        editor.changeText(busy.identity, name = "ignored")
        editor.pageTargets(busy.identity, 0)
        editor.save(busy)
        assertSame(busy, state)
        gate.complete(Unit); runCurrent()
        assertFalse(state!!.attaching)
        assertEquals(1, probeCount)
        editor.clear()
    }

    @Test fun clearAbandonsBeforeJobCancellationAndLateProbeCannotCommitOrPublish() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        probeWork = { withContext(NonCancellable) { gate.await() }; EndpointProbeOutcome.MATCH }
        open(this)
        editor.attach(state!!); runCurrent()
        val operation = checkNotNull(lastOperation)
        editor.clear()
        assertNull(state)
        assertFalse(operation.grantPublication())
        gate.complete(Unit); runCurrent()
        assertNull(state)
        assertEquals(0, commitCount)
    }

    @Test fun replacingFormWhilePreparationIgnoresCancellationCannotCreateLateRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        prepareWork = { withContext(NonCancellable) { gate.await() } }
        open(this)
        editor.attach(state!!); runCurrent()
        val retired = checkNotNull(lastOperation)
        editor.open(ref); runCurrent()
        val fresh = state!!
        assertFalse(retired.grantPublication())
        gate.complete(Unit); runCurrent()
        assertSame(fresh, state)
        assertEquals(0, probeCount + commitCount)
        editor.clear()
    }

    @Test fun retiredLeaseCannotReceiveMatchedResult() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        probeWork = { gate.await(); EndpointProbeOutcome.MATCH }
        open(this)
        editor.attach(state!!); runCurrent()
        access.revokeAll()
        lease = null
        gate.complete(Unit); runCurrent()
        assertNull(state)
        assertEquals(0, commitCount)
    }

    @Test fun ownedRestoreIsExplicitAndRequiresRefreshedRecordAfterRevisionChanges() = runTest(dispatcher) {
        ref = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4)
        storedKind = EndpointAttachmentResult.Kind.REATTACHED_INACTIVE
        refresh = true
        open(this)
        editor.attach(state!!, EndpointAttachmentAction.REATTACH_EXACT_OWNED); runCurrent()
        assertEquals(EndpointAttachmentAction.REATTACH_EXACT_OWNED, lastAction)
        assertEquals(SavedEndpointAttachmentStatus.REATTACHED_INACTIVE, state!!.attachmentStatus)
        assertTrue(state!!.attachmentRequiresRefresh)
        assertFalse(state!!.attachmentReady)
        val done = state!!
        editor.attach(done); editor.changeText(done.identity, name = "ignored"); editor.save(done)
        assertSame(done, state)
        assertEquals(1, commitCount)
        editor.clear()
    }

    @Test fun exactExistingActivePairIsReportedWithoutClaimingFreshVerification() = runTest(dispatcher) {
        present = true
        storedKind = EndpointAttachmentResult.Kind.ALREADY_PRESENT
        storedActive = true
        open(this)
        editor.attach(state!!); runCurrent()
        assertEquals(1, observedCount)
        assertEquals(0, probeCount + commitCount)
        assertEquals(SavedEndpointAttachmentStatus.PRESENT_ACTIVE, state!!.attachmentStatus)
        editor.clear()
    }

    @Test fun everyProbeFailureIsTransientAndRetryRequiresAnotherExplicitClick() = runTest(dispatcher) {
        open(this)
        EndpointProbeOutcome.entries.filter { it != EndpointProbeOutcome.MATCH }.forEachIndexed { index, reason ->
            probeWork = { reason }
            editor.attach(state!!); runCurrent()
            assertEquals(SavedEndpointAttachmentStatus.fromProbe(reason), state!!.attachmentStatus)
            assertFalse(state!!.attaching)
            assertEquals(index + 1, probeCount)
            runCurrent()
            assertEquals(index + 1, probeCount)
        }
        assertEquals(0, commitCount)
        assertThrows(IllegalStateException::class.java) { SavedEndpointAttachmentStatus.fromProbe(EndpointProbeOutcome.MATCH) }
        editor.clear()
    }

    @Test fun ownedSourceDefaultsToAddAndNonOwnedSourceCannotRequestRestore() = runTest(dispatcher) {
        open(this)
        editor.attach(state!!, EndpointAttachmentAction.REATTACH_EXACT_OWNED); runCurrent()
        assertEquals(0, prepareCount + probeCount)
        editor.clear()
        ref = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4)
        open(this)
        editor.attach(state!!); runCurrent()
        assertEquals(EndpointAttachmentAction.ADD_NEW, lastAction)
        assertEquals(1, commitCount)
        editor.clear()
    }

    @Test fun restoredRecordReopenedAtNewRevisionRequiresExplicitSaveBeforeAnyNewProbe() = runTest(dispatcher) {
        ref = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4)
        storedKind = EndpointAttachmentResult.Kind.REATTACHED_INACTIVE
        refresh = true
        open(this)
        editor.attach(state!!, EndpointAttachmentAction.REATTACH_EXACT_OWNED); runCurrent()
        assertEquals(1, probeCount)
        val oldDecision = decision
        val oldSource = oldDecision.source
        every { oldSource.matches(any()) } returns false
        editor.clear()
        ref = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 5)
        open(this, oldDecision)
        assertTrue(state!!.attachmentSourceChanged)
        assertFalse(state!!.attachmentReady)
        assertEquals(oldDecision.candidate.name, state!!.name)
        assertEquals(oldDecision.candidate.url, state!!.url)
        assertSame(oldDecision.target, state!!.target)
        editor.attach(state!!); runCurrent()
        assertEquals(1, probeCount)
        editor.save(state!!); runCurrent()
        assertTrue(state!!.attachmentReady)
        assertFalse(state!!.attachmentSourceChanged)
        assertEquals(4L, decision.revision)
        assertEquals(ref, decision.source.ref)
        assertEquals(1, probeCount)
        val oldTarget = oldDecision.target
        coVerify(exactly = 1) { decisions.saveOffline(owner, any(), any(), any(), oldTarget, 3L, any()) }
        editor.clear()
    }

    @Test fun changedTypedFingerprintAtSameRefRetainsIntentButCannotAdvertiseAttachmentReadiness() = runTest(dispatcher) {
        open(this)
        val oldDecision = decision
        val oldSource = oldDecision.source
        every { oldSource.matches(any()) } returns false
        editor.clear()
        open(this, oldDecision)
        assertTrue(state!!.attachmentSourceChanged)
        assertFalse(state!!.attachmentReady)
        assertEquals(ref, oldDecision.source.ref)
        editor.attach(state!!); runCurrent()
        assertEquals(0, prepareCount + probeCount)
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
        editor.clear()
    }

    private fun open(scope: TestScope, priorDecision: EndpointRecoveryDecision? = null) {
        val binding = mockk<EndpointDecisionSourceBinding> { every { this@mockk.ref } returns this@SavedEndpointAttachmentEditorTest.ref }
        val target = mockk<EndpointDecisionTarget> {
            every { chainId } returns "target"
            every { family } returns EndpointDecisionTarget.Family.SUBSTRATE
            every { expectedGenesis } returns "0x" + "11".repeat(32)
        }
        decision = mockk {
            every { id } returns 19L
            every { revision } returns 3L
            every { source } returns binding
            every { candidate } returns EndpointDecisionCandidate("Chosen", "wss://example.invalid/")
            every { this@mockk.target } returns target
        }
        coEvery { decisions.prepareSource(owner, any(), ref, any()) } coAnswers {
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(binding))
        }
        coEvery { decisions.read(owner, any(), ref.source, ref.rowId, any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(if (persisted) priorDecision ?: decision else null))
        }
        coEvery { decisions.readTargets(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(mockk {
                every { options } returns emptyList()
                every { previousBeforeRowId } returns null
                every { nextAfterRowId } returns null
            }))
        }
        coEvery { decisions.saveOffline(owner, any(), any(), any(), any(), any(), any()) } coAnswers {
            val savedSource = arg<EndpointDecisionSourceBinding>(2)
            val savedCandidate = arg<EndpointDecisionCandidate>(3)
            val savedTarget = arg<EndpointDecisionTarget>(4)
            val savedRevision = (arg<Long?>(5) ?: 0L) + 1
            decision = mockk {
                every { id } returns 19L
                every { revision } returns savedRevision
                every { source } returns savedSource
                every { candidate } returns savedCandidate
                every { this@mockk.target } returns savedTarget
            }
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.success(decision))
        }
        editor = SavedEndpointDecisionEditor(owner, decisions, scope, { lease?.takeIf { access.isCurrent(owner, it) } },
            { state = it }, attachments)
        editor.open(ref)
        scope.runCurrent()
    }

    private fun result(): EndpointAttachmentResult = mockk {
        every { kind } returns storedKind
        every { isActive } returns storedActive
        every { requiresSourceRefresh } returns refresh
    }
}
