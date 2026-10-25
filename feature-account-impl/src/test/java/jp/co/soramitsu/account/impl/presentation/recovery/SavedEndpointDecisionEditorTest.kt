package jp.co.soramitsu.account.impl.presentation.recovery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Form state and callback fencing only; native SQL/commit coverage lives in core-db. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointDecisionEditorTest {
    private val owner = Any()
    private val operations = mockk<SavedEndpointDecisions>()
    private var lease: SavedEndpointAccess.Lease? = SavedEndpointAccess.Lease()
    private val ref = EndpointRecordRef(EndpointInventorySource.CURRENT, 7)
    private val binding = mockk<EndpointDecisionSourceBinding> { every { ref } returns this@SavedEndpointDecisionEditorTest.ref }
    private val target = mockk<EndpointDecisionTarget> { every { chainId } returns "chosen-network" }
    private val option = option(1, "Network", target)
    private var state: SavedEndpointDecisionForm? = null
    private lateinit var editor: SavedEndpointDecisionEditor

    private fun defaults() {
        coEvery { operations.prepareSource(owner, any(), any(), any()) } coAnswers {
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(binding))
        }
        coEvery { operations.read(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(null))
        }
        coEvery { operations.readTargets(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(page(listOf(option))))
        }
    }

    @Test fun lockedActionsDoNotReadAndClearImmediatelyRemovesAllState() = runTest {
        defaults()
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        lease = null
        editor.open(ref)
        editor.retryLoad(Any()); editor.changeText(Any(), name = "private")
        editor.chooseTarget(Any(), option); editor.pageTargets(Any(), 0); editor.retryTargets(Any())
        assertNull(state)
        coVerify(exactly = 0) { operations.prepareSource(any(), any(), any(), any()) }
        lease = SavedEndpointAccess.Lease()
        editor.open(ref); runCurrent()
        assertNotNull(state)
        editor.clear()
        assertNull(state)
    }

    @Test fun firstDecisionIsEmptyAndStrictUtf8EditingNeverTrimsOrTruncates() = runTest {
        defaults()
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        assertEquals("", state!!.name); assertEquals("", state!!.url); assertNull(state!!.target)
        val id = state!!.identity
        val valid = "𒀀".repeat(128)
        editor.changeText(id, name = valid, url = "  https://example.invalid/a?x=1  ")
        assertEquals(valid, state!!.name)
        assertEquals("  https://example.invalid/a?x=1  ", state!!.url)
        editor.changeText(id, name = valid + "a")
        assertEquals(valid, state!!.name); assertTrue(state!!.inputRejected)
        editor.changeText(id, name = "\uD800")
        assertEquals(valid, state!!.name); assertTrue(state!!.inputRejected)
        editor.changeText(id, name = "chosen", url = "x".repeat(8192))
        assertFalse(state!!.inputRejected)
        editor.changeText(id, url = "x".repeat(8193))
        assertEquals(8192, state!!.url.length); assertTrue(state!!.inputRejected)
        assertFalse(state.toString().contains("chosen"))
        editor.clear()
    }

    @Test fun existingDecisionKeepsExactCandidateTextAndRevisionForNextExplicitSave() = runTest {
        defaults()
        val prior = decision(9, "  chosen  ", "wss://example.invalid/exact?x=%20")
        coEvery { operations.read(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(prior))
        }
        coEvery { operations.saveOffline(owner, any(), binding, any(), target, 9, any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.success(decision(10, "  chosen  ", prior.candidate.url)))
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        assertEquals("  chosen  ", state!!.name); assertEquals(prior.candidate.url, state!!.url)
        editor.save(state!!); editor.save(state!!); runCurrent()
        assertTrue(state!!.saved)
        coVerify(exactly = 1) { operations.saveOffline(owner, any(), binding, any(), target, 9, any()) }
        editor.clear()
    }

    @Test fun initialFailureRetriesSameRecordButSuccessfulLoadCannotOverwriteEdits() = runTest {
        defaults()
        coEvery { operations.prepareSource(owner, any(), ref, any()) } coAnswers {
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.failure(IllegalStateException("private")))
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        assertTrue(state!!.loadFailed)
        editor.changeText(state!!.identity, name = "ignored")
        assertEquals("", state!!.name)
        defaults()
        editor.retryLoad(state!!.identity); runCurrent()
        assertFalse(state!!.loadFailed)
        editor.changeText(state!!.identity, name = "retained")
        editor.retryLoad(state!!.identity); runCurrent()
        assertEquals("retained", state!!.name)
        editor.clear()
    }

    @Test fun catalogPagingRetainsEditsAndRejectsStaleOrUnavailableOptions() = runTest {
        defaults()
        val unavailable = option(2, "Unavailable", null)
        val first = page(listOf(option, unavailable), next = 2)
        val newer = option(3, "Newer", target)
        val second = page(listOf(newer), previous = 3)
        coEvery { operations.readTargets(owner, any(), null, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(first))
        }
        coEvery { operations.readTargets(owner, any(), 2, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.failure(IllegalStateException("private")))
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        val id = state!!.identity
        editor.chooseTarget(id, unavailable); assertNull(state!!.target)
        editor.chooseTarget(id, option); assertSame(target, state!!.target)
        editor.changeText(id, name = "mine", url = "wss://example.invalid")
        editor.pageTargets(id, 0); runCurrent()
        editor.pageTargets(id, 1); editor.pageTargets(id, 1); runCurrent()
        assertTrue(state!!.targetsFailed); assertEquals("mine", state!!.name)
        editor.chooseTarget(id, option); assertEquals("Network", state!!.targetName)
        coEvery { operations.readTargets(owner, any(), 2, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(second))
        }
        editor.retryTargets(id); runCurrent()
        editor.chooseTarget(id, option); assertEquals("Network", state!!.targetName)
        editor.chooseTarget(id, newer); assertEquals("Newer", state!!.targetName)
        assertFalse(state!!.choosingTarget)
        editor.pageTargets(id, 0); runCurrent()
        editor.pageTargets(id, 1); runCurrent()
        coEvery { operations.readTargets(owner, any(), null, 3, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(first))
        }
        editor.pageTargets(id, -1); runCurrent()
        assertSame(first, state!!.targets); assertEquals("mine", state!!.name)
        coVerify(exactly = 3) { operations.readTargets(owner, any(), 2, null, any()) }
        editor.clear()
    }

    @Test fun saveRequiresCurrentDisplayedSnapshotAndValidCompleteCandidate() = runTest {
        defaults()
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        val id = state!!.identity
        editor.save(state!!)
        editor.chooseTarget(id, option)
        editor.save(state!!); assertTrue(state!!.inputRejected)
        editor.changeText(id, url = "wss://example.invalid")
        val old = state!!
        editor.changeText(id, name = "edited")
        editor.save(old)
        coVerify(exactly = 0) { operations.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
        editor.clear()
    }

    @Test fun conflictReloadRetainsEditsAndRequiresAnotherExplicitSaveAtLatestRevision() = runTest {
        defaults()
        coEvery { operations.saveOffline(owner, any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.failure(IllegalStateException("conflict")))
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        val id = state!!.identity
        editor.chooseTarget(id, option)
        editor.changeText(id, name = "my edit", url = "wss://example.invalid")
        editor.save(state!!); runCurrent()
        assertTrue(state!!.saveFailed); assertEquals("my edit", state!!.name)
        coEvery { operations.read(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(decision(14, "other", "wss://other.invalid")))
        }
        editor.reloadAfterFailure(id); runCurrent()
        assertFalse(state!!.saveFailed); assertEquals("my edit", state!!.name)
        assertEquals("wss://example.invalid", state!!.url)
        coVerify(exactly = 1) { operations.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
        coEvery { operations.saveOffline(owner, any(), binding, any(), target, 14, any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(
                Result.success(decision(15, "my edit", "wss://example.invalid")))
        }
        editor.save(state!!); runCurrent()
        assertTrue(state!!.saved)
        editor.clear()
    }

    @Test fun staleCatalogRetryCannotCancelPendingOrFailedSourceReload() = runTest {
        defaults()
        coEvery { operations.read(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(decision(3, "mine", "wss://example.invalid")))
        }
        coEvery { operations.readTargets(owner, any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.failure(IllegalStateException("catalog")))
        }
        coEvery { operations.saveOffline(owner, any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.failure(IllegalStateException("conflict")))
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        val id = state!!.identity
        assertTrue(state!!.targetsFailed)
        editor.save(state!!); runCurrent()
        assertTrue(state!!.saveFailed)
        val held = CompletableDeferred<Unit>()
        coEvery { operations.prepareSource(owner, any(), ref, any()) } coAnswers {
            held.await()
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.failure(IllegalStateException("reload")))
        }
        editor.reloadAfterFailure(id); runCurrent()
        assertTrue(state!!.loading)
        editor.retryTargets(id); runCurrent()
        coVerify(exactly = 1) { operations.readTargets(owner, any(), any(), any(), any()) }
        held.complete(Unit); runCurrent()
        assertFalse(state!!.loading); assertTrue(state!!.loadFailed)
        editor.retryTargets(id); runCurrent()
        coVerify(exactly = 1) { operations.readTargets(owner, any(), any(), any(), any()) }
        defaults()
        editor.retryLoad(id); runCurrent()
        assertFalse(state!!.loading); assertFalse(state!!.loadFailed)
        assertEquals("mine", state!!.name)
        editor.clear()
    }

    @Test fun lateInitialResultCannotRepopulateClosedOrReplacementFormEvenAwayAndBack() = runTest {
        for (failure in listOf(false, true)) {
            defaults()
            val held = CompletableDeferred<Unit>()
            coEvery { operations.prepareSource(owner, any(), ref, any()) } coAnswers {
                withContext(NonCancellable) { held.await() }
                arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(
                    if (failure) Result.failure(IllegalStateException("late")) else Result.success(binding))
            }
            editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
            editor.open(ref); runCurrent()
            val staleId = state!!.identity
            editor.clear(); defaults(); editor.open(ref); runCurrent()
            val fresh = state!!
            editor.changeText(staleId, name = "late"); editor.retryLoad(staleId)
            held.complete(Unit); runCurrent()
            assertSame(fresh, state)
            editor.clear()
        }
    }

    @Test fun lateSaveSuccessOrFailureAfterLeaseReplacementNeverPublishes() = runTest {
        for (failure in listOf(false, true)) {
            defaults()
            val held = CompletableDeferred<Unit>()
            coEvery { operations.saveOffline(owner, any(), any(), any(), any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { held.await() }
                arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(
                    if (failure) Result.failure(IllegalStateException("late"))
                    else Result.success(decision(1, "mine", "wss://example.invalid")))
            }
            editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
            editor.open(ref); runCurrent()
            editor.chooseTarget(state!!.identity, option)
            editor.changeText(state!!.identity, name = "mine", url = "wss://example.invalid")
            editor.save(state!!); runCurrent()
            lease = SavedEndpointAccess.Lease()
            held.complete(Unit); runCurrent()
            assertNull(state)
            editor.clear()
        }
    }

    @Test fun adapterSuppressedPublicationClearsAReplacedLeaseRatherThanLeavingLoadedText() = runTest {
        defaults()
        val held = CompletableDeferred<Unit>()
        coEvery { operations.readTargets(owner, any(), any(), any(), any()) } coAnswers {
            held.await()
            // Real lease adapter intentionally returns without publishing after revocation.
        }
        editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
        editor.open(ref); runCurrent()
        editor.changeText(state!!.identity, name = "private edit")
        assertEquals("private edit", state!!.name)
        lease = null
        held.complete(Unit); runCurrent()
        assertNull(state)
    }

    @Test fun lateCatalogAndDecisionResponsesCannotOverwriteReplacementFormOrNewText() = runTest {
        for (duringDecision in listOf(false, true)) {
            defaults()
            val held = CompletableDeferred<Unit>()
            if (duringDecision) {
                coEvery { operations.read(owner, any(), any(), any(), any()) } coAnswers {
                    withContext(NonCancellable) { held.await() }
                    arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(
                        Result.success(decision(22, "old", "wss://old.invalid")))
                }
            } else {
                coEvery { operations.readTargets(owner, any(), any(), any(), any()) } coAnswers {
                    withContext(NonCancellable) { held.await() }
                    arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(
                        Result.success(page(listOf(option(45, "old", target)))))
                }
            }
            editor = SavedEndpointDecisionEditor(owner, operations, this, { lease }, { state = it }, mockk())
            editor.open(ref); runCurrent()
            editor.clear(); defaults(); editor.open(ref); runCurrent()
            editor.changeText(state!!.identity, name = "new edit")
            val replacement = state
            held.complete(Unit); runCurrent()
            assertSame(replacement, state)
            editor.clear()
        }
    }

    private fun option(row: Long, label: String, captured: EndpointDecisionTarget?): EndpointDecisionTargetOption =
        mockk { every { rowId } returns row; every { name } returns label; every { target } returns captured }

    private fun page(
        rows: List<EndpointDecisionTargetOption>, previous: Long? = null, next: Long? = null
    ): EndpointDecisionTargetPage = mockk {
        every { options } returns rows
        every { previousBeforeRowId } returns previous
        every { nextAfterRowId } returns next
    }

    private fun decision(rev: Long, label: String, endpoint: String): EndpointRecoveryDecision = mockk {
        every { revision } returns rev
        every { source } returns binding
        every { candidate } returns EndpointDecisionCandidate(label, endpoint)
        every { target } returns this@SavedEndpointDecisionEditorTest.target
    }
}
