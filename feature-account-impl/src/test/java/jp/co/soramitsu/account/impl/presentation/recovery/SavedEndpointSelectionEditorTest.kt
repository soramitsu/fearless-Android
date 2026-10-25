package jp.co.soramitsu.account.impl.presentation.recovery

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelectionOperation
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelections
import jp.co.soramitsu.account.impl.domain.recovery.SelectionCoreTestBridge
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionSourceBinding
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetPage
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionOwner
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionResult
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Actual controller/adapter/authority in their module boundaries; fake storage and runtime observations. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointSelectionEditorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() { Dispatchers.setMain(dispatcher) }

    @After fun after() { Dispatchers.resetMain() }

    private class Fixture(scope: CoroutineScope, family: EndpointDecisionTarget.Family = EndpointDecisionTarget.Family.SUBSTRATE) {
        val owner = Any()
        val access = SavedEndpointAccess { 0L }
        val lane = EndpointMutationLane()
        val decision = SelectionCoreTestBridge.decision(
            1, 2,
            SelectionCoreTestBridge.sourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 4, 2), "a".repeat(64)),
            EndpointDecisionCandidate("Public fixture", "https://public.invalid/"),
            SelectionCoreTestBridge.target("chain", "b".repeat(64), family, "c".repeat(64))
        )
        val request = EndpointSelectionRequest(decision, EndpointSelectionOwner(4, 2, 7))
        val node = ChainNode(decision.candidate.url, decision.candidate.name, true, false)
        var attached = true
        var failCommit = false
        var prepared = 0
        var committed = 0
        var observeGate: suspend () -> Unit = {}
        var prepareGate: suspend () -> Unit = {}
        var commitGate: suspend () -> Unit = {}
        var runtimeCurrent = true
        val runtime = MutableStateFlow<EndpointRuntimeStatus?>(null)
        val published = mutableListOf<SavedEndpointSelectionState?>()
        val lease: SavedEndpointAccess.Lease
        val adapter: SavedEndpointSelections
        val editor: SavedEndpointSelectionEditor
        init {
            val token = access.begin(owner)

            check(access.approve(owner, token.id))

            lease = checkNotNull(access.claim(owner, token.id))
            val store = object : SavedEndpointSelections.Storage {
                override suspend fun observe(
                    decision: EndpointRecoveryDecision
                ): EndpointMutationLane.RuntimeRead<EndpointSelectionRequest?> {
                    observeGate()
                    return SelectionCoreTestBridge.readRuntime(lane, "chain") { request.takeIf { attached } }
                }
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
                    commitGate()
                    if (failCommit) error("Public fixture physical commit failure")
                    SelectionCoreTestBridge.result(capture)
                }
            }
            adapter = SavedEndpointSelections(access, store, lane) { runtime }
            editor = SavedEndpointSelectionEditor(owner, adapter, scope, {
                lease.takeIf { access.isCurrent(owner, it) }
            }) { published += it }
        }
        fun open() = editor.open(decision)
        fun select() = editor.select(checkNotNull(editor.state))
        fun status(
            phase: EndpointRuntimeStatus.Phase,
            applied: Boolean = true,
            logical: ChainNode = node
        ) = SelectionCoreTestBridge.runtimeStatus(phase, applied, logical) { runtimeCurrent }
        fun phase() = editor.state?.phase
        fun runtimeState() = editor.state?.runtimeState
    }

    @Test fun openingOnlyReadsEligibilityAndNeverCreatesAnOperation() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Phase.AVAILABLE, f.phase())
        assertEquals(0, f.prepared + f.committed)

        assertNull(f.runtimeState())
    }

    @Test fun missingAttachmentNeverEnablesSelection() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.attached = false

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Phase.NOT_ATTACHED, f.phase())

        assertEquals(0, f.committed)
    }

    @Test fun changedRenderedStateCannotAuthorizeOldButton() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()

        val old = checkNotNull(f.editor.state)
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED)

        runCurrent()
        assertNotSame(old, f.editor.state)

        f.editor.select(old)

        runCurrent()

        assertEquals(0, f.prepared)
        f.select()

        runCurrent()

        assertEquals(1, f.committed)
    }

    @Test fun overlappingIntentMakesHeldEligibilityStaleWithoutRoomEmission() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()

        val old = checkNotNull(f.editor.state)
        f.lane.abandon(f.lane.admit(listOf("chain")))
        assertEquals(SavedEndpointSelectionState.Phase.STALE, old.phase)
        f.editor.select(old)

        runCurrent()

        assertEquals(0, f.committed)
    }

    @Test fun explicitSelectAcknowledgesOnlyAfterPhysicalCompletionAndOnlyOnce() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.commitGate = { gate.await() }
        f.open()

        runCurrent()

        val button = checkNotNull(f.editor.state)

        f.editor.select(button)

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Phase.SAVING, f.phase())

        assertEquals(1, f.committed)
        f.editor.select(button)

        gate.complete(Unit)

        runCurrent()
        assertEquals(1, f.prepared)

        assertEquals(SavedEndpointSelectionState.Phase.SAVED, f.phase())
        assertEquals(SavedEndpointSelectionState.Runtime.NOT_IN_USE, f.runtimeState())
    }

    @Test fun clearDuringPreparationCannotCommitOrRepublish() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.prepareGate = { gate.await() }
        f.open()

        runCurrent()

        f.select()

        runCurrent()

        f.editor.clear()

        gate.complete(Unit)

        runCurrent()
        assertEquals(0, f.committed)

        assertNull(f.editor.state)

        assertNull(f.published.last())
    }

    @Test fun revocationBeforePreparationReturnsCannotCommitOrShowSuccess() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.prepareGate = { gate.await() }
        f.open()

        runCurrent()

        f.select()

        runCurrent()

        f.access.revokeAll()

        gate.complete(Unit)

        runCurrent()
        assertEquals(0, f.committed)

        assertEquals(SavedEndpointSelectionState.Phase.STALE, f.phase())
    }

    @Test fun failedPhysicalCommitNeverClaimsSaved() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.failCommit = true

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        assertEquals(1, f.committed)

        assertEquals(SavedEndpointSelectionState.Phase.FAILED, f.phase())

        assertNull(f.runtimeState())
    }

    @Test fun alreadyGrantedCommitMayFinishAfterClearButCannotRepublish() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()
        f.commitGate = { withContext(NonCancellable) { gate.await() } }
        f.open()

        runCurrent()

        f.select()

        runCurrent()

        f.editor.clear()

        gate.complete(Unit)

        runCurrent()
        assertEquals(1, f.committed)

        assertNull(f.editor.state)

        assertNull(f.published.last())
    }

    @Test fun heldReadAfterClearCannotExposeOwnerOrEnableAction() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()
        f.observeGate = { withContext(NonCancellable) { gate.await() } }
        f.open()

        runCurrent()

        f.editor.clear()

        gate.complete(Unit)

        runCurrent()
        assertNull(f.editor.state)

        assertEquals(0, f.prepared + f.committed)
    }

    @Test fun connectedRequiresExactCurrentLogicalNodeAndAppliedReceipt() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED, logical = f.node.copy(name = "Old name"))

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Runtime.PENDING, f.runtimeState())
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED, applied = false)

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Runtime.PENDING, f.runtimeState())
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED)

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Runtime.CONNECTED, f.runtimeState())
        f.runtimeCurrent = false
        assertEquals(SavedEndpointSelectionState.Runtime.PENDING, f.runtimeState())
    }

    @Test fun pausedConnectingAndUnavailableRemainSeparateFromDurableSaved() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        for ((phase, expected) in listOf(
            EndpointRuntimeStatus.Phase.PAUSED to SavedEndpointSelectionState.Runtime.PAUSED,
            EndpointRuntimeStatus.Phase.UNAVAILABLE to SavedEndpointSelectionState.Runtime.UNAVAILABLE,
            EndpointRuntimeStatus.Phase.CONNECTING to SavedEndpointSelectionState.Runtime.CONNECTING
        )) {
            f.runtime.value = f.status(phase)

            runCurrent()
            assertEquals(SavedEndpointSelectionState.Phase.SAVED, f.phase())

            assertEquals(expected, f.runtimeState())
        }
    }

    @Test fun laterIntentImmediatelyRetiresSavedAndConnectedLabels() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED)

        runCurrent()
        val held = checkNotNull(f.editor.state)

        assertEquals(SavedEndpointSelectionState.Runtime.CONNECTED, held.runtimeState)
        f.lane.admitCatalogReplacement()
        assertEquals(SavedEndpointSelectionState.Phase.STALE, held.phase)

        assertNull(held.runtimeState)
    }

    @Test fun solanaPreferenceNeverClaimsConnectedFromSocketStatus() = runTest(dispatcher) {
        val f = Fixture(backgroundScope, EndpointDecisionTarget.Family.SOLANA)

        f.open()

        runCurrent()

        f.select()

        runCurrent()
        f.runtime.value = f.status(EndpointRuntimeStatus.Phase.CONNECTED)

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Runtime.PREFERRED, f.runtimeState())
        assertFalse(f.editor.state.toString().contains("public.invalid"))
    }
    private class FormFixture(val selection: Fixture, scope: CoroutineScope) {
        var form: SavedEndpointDecisionForm? = null
        private val decisions = mockk<SavedEndpointDecisions>()
        val editor: SavedEndpointDecisionEditor
        init {
            val d = selection.decision
            coEvery { decisions.prepareSource(any(), any(), any(), any()) } coAnswers {
                arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(d.source))
            }
            coEvery { decisions.read(any(), any(), any(), any(), any()) } coAnswers {
                arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(d))
            }
            val page = mockk<EndpointDecisionTargetPage> {
                every { options } returns emptyList()
                every { previousBeforeRowId } returns null
                every { nextAfterRowId } returns null
            }
            coEvery { decisions.readTargets(any(), any(), any(), any(), any()) } coAnswers {
                arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(page))
            }
            editor = SavedEndpointDecisionEditor(
                selection.owner, decisions, scope,
                {
                    selection.lease.takeIf { selection.access.isCurrent(selection.owner, it) }
                }, { form = it }, mockk(), selection.adapter
            )
        }
        fun open() = editor.open(selection.decision.source.ref)
    }

    @Test fun actualFormReadDoesNotSelectAndOnlyExactRenderedFormCanSelect() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()
        val shown = checkNotNull(ui.form)

        assertEquals(SavedEndpointSelectionState.Phase.AVAILABLE, shown.selection?.phase)
        assertEquals(0, f.prepared + f.committed)
        ui.editor.select(shown.copy())

        runCurrent()

        assertEquals(0, f.committed)
        ui.editor.select(shown)

        runCurrent()

        assertEquals(1, f.committed)
        assertEquals(SavedEndpointSelectionState.Phase.SAVED, ui.form?.selection?.phase)
    }

    @Test fun actualNameEditRetiresHeldSelectionBeforeCommit() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.prepareGate = { gate.await() }
        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()

        val shown = checkNotNull(ui.form)
        ui.editor.select(shown)

        runCurrent()

        ui.editor.changeText(shown.identity, name = "New label")
        gate.complete(Unit)

        runCurrent()
        assertEquals(0, f.committed)

        assertEquals("New label", ui.form?.name)

        assertNull(ui.form?.selection)
    }

    @Test fun actualUrlEditCannotReusePreviousOwnerObservation() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()
        val shown = checkNotNull(ui.form)

        ui.editor.changeText(shown.identity, url = "https://changed.invalid/")
        ui.editor.select(shown)

        runCurrent()
        assertEquals(0, f.committed)

        assertNull(ui.form?.selection)
    }

    @Test fun actualInvalidTextIntentStillRetiresHeldSelection() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.prepareGate = { gate.await() }
        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()

        val shown = checkNotNull(ui.form)
        ui.editor.select(shown)

        runCurrent()

        ui.editor.changeText(shown.identity, name = "x".repeat(513))
        gate.complete(Unit)

        runCurrent()
        assertEquals(0, f.committed)

        assertTrue(ui.form!!.inputRejected)

        assertNull(ui.form?.selection)
    }

    @Test fun actualTargetRefreshRetiresOldSelectionWithoutImplicitCommit() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()

        f.prepareGate = { gate.await() }
        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()

        val shown = checkNotNull(ui.form)
        ui.editor.select(shown)

        runCurrent()

        ui.editor.pageTargets(shown.identity, 0)
        gate.complete(Unit)

        runCurrent()
        assertEquals(0, f.committed)

        assertTrue(ui.form!!.choosingTarget)
        ui.editor.select(shown)

        runCurrent()

        assertEquals(0, f.committed)
    }

    @Test fun actualClearRemovesAllFormTextAndLateCommitAcknowledgment() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val gate = CompletableDeferred<Unit>()
        f.commitGate = { withContext(NonCancellable) { gate.await() } }
        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()

        ui.editor.select(checkNotNull(ui.form))

        runCurrent()
        ui.editor.clear()

        assertNull(ui.form)

        gate.complete(Unit)

        runCurrent()
        assertEquals(1, f.committed)

        assertNull(ui.form)
    }

    @Test fun actualRefreshAfterNewIntentCreatesReadOnlyNewRenderedObservation() = runTest(dispatcher) {
        val f = Fixture(backgroundScope)

        val ui = FormFixture(f, backgroundScope)

        ui.open()

        runCurrent()
        f.lane.abandon(f.lane.admit(listOf("chain")))

        runCurrent()
        val stale = checkNotNull(ui.form)

        assertEquals(SavedEndpointSelectionState.Phase.STALE, stale.selection?.phase)
        ui.editor.refreshSelection(stale)

        runCurrent()
        assertEquals(SavedEndpointSelectionState.Phase.AVAILABLE, ui.form?.selection?.phase)

        assertEquals(0, f.committed)
        ui.editor.select(stale)

        runCurrent()

        assertEquals(0, f.committed)
    }
}
