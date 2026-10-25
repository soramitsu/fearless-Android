package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.*
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointInventory
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.coredb.recovery.*
import jp.co.soramitsu.coredb.recovery.decision.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** ViewModel integration with the real one-shot access protocol; PIN approval and persistence are synthetic. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointDecisionViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val inventory = mockk<SavedEndpointInventory>()
    private val decisions = mockk<SavedEndpointDecisions>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val access = SavedEndpointAccess { 0L }
    private val health = MutableStateFlow<PinHealth?>(PinHealth.HEALTHY)
    private val owner = slot<Any>()
    private val request = slot<String>()
    private val models = mutableListOf<SavedEndpointsViewModel>()
    private val stores = mutableListOf<ViewModelStore>()
    private val legacy = LegacyEndpointPage(true, listOf(legacyRecord(7)), null, null)
    private val target = mockk<EndpointDecisionTarget> { every { chainId } returns "chosen-network" }
    private val option = option(10, "Local network", target)
    private lateinit var model: SavedEndpointsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        WalletViewOnlyAccess.pinResetCompleted()
        mockkObject(WalletPinHealth)
        every { WalletPinHealth.health } returns health
        every { router.requestSavedEndpointUnlock(capture(owner), capture(request)) } returns true
        coEvery { inventory.readPage(null, null) } returns legacy
        coEvery { inventory.readInventoryPage(any(), null, null) } coAnswers { page(arg(0)) }
        defaults()
        model = newModel()
    }

    @After fun teardown() {
        stores.forEach { it.clear() }
        models.forEach { it.viewModelScope.cancel(); it.coroutineContext.cancel() }
        access.revokeAll()
        WalletViewOnlyAccess.pinResetCompleted()
        unmockkObject(WalletPinHealth)
        Dispatchers.resetMain()
    }

    @Test fun everyDecisionCallbackWhileLockedIsInertAndDoesNotReadOrSave() = runTest(dispatcher) {
        val fake = SavedEndpointDecisionForm(Any(), ref())
        model.editRecord(ref())
        callbacks(fake)
        runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify { decisions wasNot Called }
        coVerify { inventory wasNot Called }
    }

    @Test fun decisionReadUsesOnlyTheLeaseClaimedAfterMatchingApprovedPinRequest() = runTest(dispatcher) {
        runCurrent()
        model.unlock(); model.paused()
        assertFalse(access.approve(Any(), request.captured))
        model.editRecord(EndpointRecordRef(EndpointInventorySource.LEGACY, 7))
        coVerify { decisions wasNot Called }
        // The locked edit retires the pending request; a new explicit request is required.
        assertFalse(access.approve(owner.captured, request.captured))
        unlock(); runCurrent()
        val admittedOwner = owner.captured
        model.editRecord(EndpointRecordRef(EndpointInventorySource.LEGACY, 7)); runCurrent()
        val capturedLease = slot<SavedEndpointAccess.Lease>()
        coVerify(exactly = 1) { decisions.prepareSource(admittedOwner, capture(capturedLease),
            EndpointRecordRef(EndpointInventorySource.LEGACY, 7), any()) }
        assertTrue(access.isCurrent(admittedOwner, capturedLease.captured))
        coVerify(exactly = 1) { decisions.read(admittedOwner, capturedLease.captured, EndpointInventorySource.LEGACY, 7, any()) }
        coVerify(exactly = 1) { decisions.readTargets(admittedOwner, capturedLease.captured, null, null, any()) }
        assertFalse(access.approve(admittedOwner, request.captured))
        model.paused()
        assertFalse(access.isCurrent(admittedOwner, capturedLease.captured))
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun eachCollectionAdmitsOnlyItsListedSourceRowAndExactOwnedRevision() = runTest(dispatcher) {
        unlock(); runCurrent()
        for (source in EndpointInventorySource.entries) {
            model.selectSource(source); runCurrent()
            val exact = ref(source)
            val wrongSource = if (source == EndpointInventorySource.OWNED) EndpointInventorySource.CURRENT else EndpointInventorySource.OWNED
            model.editRecord(ref(wrongSource))
            model.editRecord(ref(source, row = 8))
            if (source == EndpointInventorySource.OWNED) model.editRecord(ref(source, revision = 8))
            runCurrent()
            assertNull(model.state.value.decisionForm)
            coVerify(exactly = 0) { decisions.prepareSource(any(), any(), exact, any()) }
            model.editRecord(exact); runCurrent()
            assertEquals(exact, form().ref)
            coVerify(exactly = 1) { decisions.prepareSource(any(), any(), exact, any()) }
            model.returnToList(); runCurrent()
        }
        coVerify(exactly = 5) { decisions.prepareSource(any(), any(), any(), any()) }
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun loadingInventoryAndVisibleInspectionCannotOpenAForm() = runTest(dispatcher) {
        unlock(); runCurrent()
        val held = CompletableDeferred<EndpointPage>()
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } coAnswers { held.await() }
        model.selectSource(EndpointInventorySource.OWNED); runCurrent()
        model.editRecord(ref()); runCurrent()
        coVerify { decisions wasNot Called }
        held.complete(page(EndpointInventorySource.OWNED)); runCurrent()
        val chunk = mockk<EndpointValueChunk>()
        every { chunk.ref } returns ref()
        every { chunk.field } returns EndpointField.NAME
        every { chunk.byteOffset } returns 0
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns chunk
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        assertNotNull(model.state.value.inventoryInspection)
        model.editRecord(ref()); runCurrent()
        coVerify { decisions wasNot Called }
        model.returnToList(); runCurrent()
        model.editRecord(ref()); runCurrent()
        assertEquals(ref(), form().ref)
    }

    @Test fun inventoryRefreshPagingAndInspectionCallbacksDoNotDiscardAnOpenForm() = runTest(dispatcher) {
        open()
        val id = form().identity
        model.changeDecisionName(id, "typed name")
        model.changeDecisionUrl(id, " wss://example.invalid/exact?x=%20 ")
        val before = form()
        model.refresh(); model.retry(); model.next(); model.previous()
        model.inspectRecord(ref(), EndpointField.NAME)
        model.inspect(7, LegacyEndpointField.NAME)
        model.editRecord(ref())
        runCurrent()
        assertSame(before, form())
        coVerify(exactly = 1) { inventory.readInventoryPage(any(), any(), any()) }
        coVerify(exactly = 0) { inventory.readInventoryChunk(any(), any(), any()) }
        coVerify(exactly = 1) { decisions.prepareSource(any(), any(), any(), any()) }
    }

    @Test fun initialFailureRetriesOnlyCurrentFormIdentityAndSuccessfulRetryKeepsTheExactRef() = runTest(dispatcher) {
        coEvery { decisions.prepareSource(any(), any(), ref(), any()) } coAnswers {
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.failure(IllegalStateException("private")))
        }
        open()
        assertTrue(form().loadFailed)
        val id = form().identity
        model.retryDecisionLoad(Any()); model.changeDecisionName(id, "ignored")
        assertEquals("", form().name)
        defaults()
        model.retryDecisionLoad(id); runCurrent()
        assertFalse(form().loadFailed); assertEquals(ref(), form().ref)
        model.changeDecisionName(id, "exact name")
        model.retryDecisionLoad(id); runCurrent()
        assertEquals("exact name", form().name)
        coVerify(exactly = 2) { decisions.prepareSource(any(), any(), ref(), any()) }
    }

    @Test fun targetPageRetryAndSelectionWrappersUseCurrentBoundaryAndKeepExactEdits() = runTest(dispatcher) {
        val first = targetPage(listOf(option), next = 10)
        val nextOption = option(20, "Second network", target)
        val second = targetPage(listOf(nextOption), previous = 20)
        coEvery { decisions.readTargets(any(), any(), null, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(first))
        }
        coEvery { decisions.readTargets(any(), any(), 10, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.failure(IllegalStateException("private")))
        }
        open()
        val id = form().identity
        model.changeDecisionName(id, "  retained name  ")
        model.changeDecisionUrl(id, "https://example.invalid/%20")
        model.pageDecisionTargets(id, 1); model.pageDecisionTargets(id, 1); runCurrent()
        assertTrue(form().targetsFailed)
        model.retryDecisionTargets(Any()); runCurrent()
        coVerify(exactly = 1) { decisions.readTargets(any(), any(), 10, null, any()) }
        coEvery { decisions.readTargets(any(), any(), 10, null, any()) } coAnswers {
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(second))
        }
        model.retryDecisionTargets(id); runCurrent()
        model.chooseDecisionTarget(id, option)
        assertNull(form().target)
        model.chooseDecisionTarget(id, nextOption)
        assertSame(target, form().target); assertEquals("Second network", form().targetName)
        assertFalse(form().choosingTarget)
        assertEquals("  retained name  ", form().name)
        assertEquals("https://example.invalid/%20", form().url)
        coVerify(exactly = 2) { decisions.readTargets(any(), any(), 10, null, any()) }
    }

    @Test fun saveWrapperRejectsOldSnapshotAndSendsOnlyExplicitExactCandidateWithCurrentLease() = runTest(dispatcher) {
        open()
        val id = form().identity
        model.chooseDecisionTarget(id, option)
        model.changeDecisionName(id, "  exact name  ")
        model.changeDecisionUrl(id, " wss://example.invalid/?x=%20 ")
        val stale = form()
        model.changeDecisionName(id, "  exact changed name  ")
        model.saveDecision(stale); runCurrent()
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
        val expected = form()
        coEvery { decisions.saveOffline(any(), any(), any(), any(), target, null, any()) } coAnswers {
            assertTrue(access.isCurrent(arg(0), arg(1)))
            val binding = arg<EndpointDecisionSourceBinding>(2)
            val candidate = arg<EndpointDecisionCandidate>(3)
            assertEquals(ref(), binding.ref)
            assertEquals(expected.name, candidate.name); assertEquals(expected.url, candidate.url)
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.success(decision(ref(), 1, candidate)))
        }
        model.saveDecision(expected); model.saveDecision(expected); runCurrent()
        assertTrue(form().saved); assertFalse(form().saving)
        coVerify(exactly = 1) { decisions.saveOffline(any(), any(), any(), any(), target, null, any()) }
        coVerify(exactly = 0) { decisions.prepareTarget(any(), any(), any(), any()) }
    }

    @Test fun conflictReloadWrapperKeepsEditsAndRequiresAnotherExplicitSaveAtObservedDecisionRevision() = runTest(dispatcher) {
        open()
        val id = form().identity
        model.chooseDecisionTarget(id, option)
        model.changeDecisionName(id, "mine"); model.changeDecisionUrl(id, "wss://mine.invalid")
        coEvery { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.failure(IllegalStateException("conflict")))
        }
        model.saveDecision(form()); runCurrent()
        assertTrue(form().saveFailed)
        val existing = decision(ref(), 9, EndpointDecisionCandidate("other", "wss://other.invalid"))
        // This conflict changes the saved decision, not the source fingerprint.
        // Return the same prepared binding; fingerprint comparison has separate core/editor tests.
        coEvery { decisions.prepareSource(any(), any(), ref(), any()) } coAnswers {
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(existing.source))
        }
        coEvery { decisions.read(any(), any(), EndpointInventorySource.OWNED, 7, any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(existing))
        }
        model.reloadDecisionAfterFailure(Any()); runCurrent()
        assertTrue(form().saveFailed)
        model.reloadDecisionAfterFailure(id); runCurrent()
        assertEquals("mine", form().name); assertEquals("wss://mine.invalid", form().url)
        assertFalse(form().saveFailed)
        assertFalse(form().loadFailed)
        assertFalse(form().attachmentReady)
        assertFalse(form().attachmentSourceChanged)
        coVerify(exactly = 2) { decisions.prepareSource(any(), any(), ref(), any()) }
        coVerify(exactly = 1) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
        coEvery { decisions.saveOffline(any(), any(), any(), any(), target, 9, any()) } coAnswers {
            arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(Result.success(decision(ref(), 10, arg(3))))
        }
        model.saveDecision(form()); runCurrent()
        assertTrue(form().saved)
        coVerify(exactly = 1) { decisions.saveOffline(any(), any(), any(), any(), target, 9, any()) }
    }

    @Test fun pauseImmediatelyClearsFormAndFencesUncooperativeSaveSuccessOrFailure() = runTest(dispatcher) {
        for (fails in listOf(false, true)) {
            model = newModel(); open()
            val id = form().identity
            model.chooseDecisionTarget(id, option)
            model.changeDecisionUrl(id, "wss://example.invalid")
            val held = CompletableDeferred<Unit>()
            coEvery { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { held.await() }
                arg<(Result<EndpointRecoveryDecision>) -> Unit>(6)(if (fails) Result.failure(IllegalStateException("late"))
                    else Result.success(decision(ref(), 1, arg(3))))
            }
            val stale = form()
            model.saveDecision(stale); runCurrent(); assertTrue(form().saving)
            model.paused()
            assertEquals(SavedEndpointsState(), model.state.value)
            callbacks(stale)
            held.complete(Unit); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
            model.resumed(); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 2) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun backClosesFormRefreshesTheSameCollectionAndOnlySecondBackLeavesDestination() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { decisions.prepareSource(any(), any(), ref(), any()) } coAnswers {
            withContext(NonCancellable) { held.await() }
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(binding(ref())))
        }
        open(); assertTrue(form().loading)
        model.back()
        assertNull(model.state.value.decisionForm)
        runCurrent()
        assertEquals(EndpointInventorySource.OWNED, model.state.value.source)
        assertNotNull(model.state.value.inventoryPage)
        verify(exactly = 0) { router.back() }
        held.complete(Unit); runCurrent(); assertNull(model.state.value.decisionForm)
        coVerify(exactly = 2) { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) }
        model.back(); model.back(); runCurrent()
        verify(exactly = 1) { router.back() }
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun switchingCollectionsRetiresOldFormAndEveryStaleFormCallback() = runTest(dispatcher) {
        open()
        val stale = form()
        model.selectSource(EndpointInventorySource.CURRENT)
        assertNull(model.state.value.decisionForm)
        runCurrent()
        model.editRecord(ref(EndpointInventorySource.CURRENT)); runCurrent()
        val fresh = form()
        callbacks(stale); runCurrent()
        assertSame(fresh, form())
        assertEquals(ref(EndpointInventorySource.CURRENT), form().ref)
        coVerify(exactly = 2) { decisions.prepareSource(any(), any(), any(), any()) }
        coVerify(exactly = 2) { decisions.readTargets(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun unknownUnreadableAndViewOnlyStatesRemoveTheWholeFormAndRequireFreshUnlock() = runTest(dispatcher) {
        for (mode in listOf("unknown", "unreadable", "viewOnly")) {
            health.value = PinHealth.HEALTHY; WalletViewOnlyAccess.pinResetCompleted()
            model = newModel(); open()
            val stale = form()
            model.changeDecisionName(stale.identity, "private text")
            when (mode) {
                "unknown" -> health.value = null
                "unreadable" -> health.value = PinHealth.UNREADABLE
                else -> WalletViewOnlyAccess.enter()
            }
            runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
            health.value = PinHealth.HEALTHY; WalletViewOnlyAccess.pinResetCompleted(); runCurrent()
            callbacks(stale); model.editRecord(ref()); model.resumed(); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 3) { decisions.prepareSource(any(), any(), any(), any()) }
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun clearedOwnerCannotPublishLateFormOrRevokeReplacementOwnersLease() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { decisions.prepareSource(any(), any(), ref(), any()) } coAnswers {
            withContext(NonCancellable) { held.await() }
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(binding(ref())))
        }
        open()
        val old = model; val stale = form()
        stores.last().clear()
        assertEquals(SavedEndpointsState(), old.state.value)
        defaults()
        model = newModel(); open()
        val fresh = form(); val freshOwner = owner.captured
        val freshLease = slot<SavedEndpointAccess.Lease>()
        coVerify(exactly = 1) { decisions.prepareSource(freshOwner, capture(freshLease), ref(), any()) }
        held.complete(Unit); runCurrent()
        callbacks(stale, old); old.editRecord(ref()); old.back(); runCurrent()
        assertEquals(SavedEndpointsState(), old.state.value)
        assertSame(fresh, form())
        assertTrue(access.isCurrent(freshOwner, freshLease.captured))
        model.changeDecisionName(fresh.identity, "new owner edit")
        assertEquals("new owner edit", form().name)
        verify(exactly = 0) { router.back() }
    }

    @Test fun replacedLeaseRejectsOldFormActionsWithoutRevokingTheNewOwner() = runTest(dispatcher) {
        open()
        val stale = form()
        val replacementOwner = Any()
        val replacement = access.begin(replacementOwner)
        assertTrue(access.approve(replacementOwner, replacement.id))
        val replacementLease = access.claim(replacementOwner, replacement.id)!!
        model.changeDecisionName(stale.identity, "must not appear")
        assertNull(model.state.value.decisionForm)
        callbacks(stale); model.editRecord(ref()); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        assertTrue(access.isCurrent(replacementOwner, replacementLease))
        coVerify(exactly = 1) { decisions.prepareSource(any(), any(), any(), any()) }
        coVerify(exactly = 0) { decisions.saveOffline(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun replacingAnIdleOwnerClearsItsEntirePageAndFormWithoutAnyOldCallback() = runTest(dispatcher) {
        for (withForm in listOf(false, true)) {
            model = newModel()
            if (withForm) open() else { unlock(); runCurrent() }
            val old = model
            assertFalse(old.state.value.locked)
            model = newModel(); open()
            val fresh = form(); val freshOwner = owner.captured
            val freshLease = slot<SavedEndpointAccess.Lease>()
            coVerify(exactly = 1) { decisions.prepareSource(freshOwner, capture(freshLease), ref(), any()) }
            // No old-owner action or lifecycle callback is needed to hide its data.
            assertEquals(SavedEndpointsState(), old.state.value)
            assertSame(fresh, form())
            assertTrue(access.isCurrent(freshOwner, freshLease.captured))
        }
    }

    @Test fun globalRevocationClearsIdleFormAndQueuedOldObserverCannotClearNewClaim() = runTest(dispatcher) {
        open()
        access.revokeAll(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        open()
        access.revokeAll()
        // Retire this observer before its queued resumption, then claim a fresh lease on the same VM.
        model.paused(); model.unlock()
        assertTrue(access.approve(owner.captured, request.captured))
        model.resumed(); runCurrent()
        assertFalse(model.state.value.locked)
        model.selectSource(EndpointInventorySource.OWNED); runCurrent()
        model.editRecord(ref()); runCurrent()
        assertEquals(ref(), form().ref)
        assertFalse(form().loading)
    }

    private fun defaults() {
        coEvery { decisions.prepareSource(any(), any(), any(), any()) } coAnswers {
            assertTrue(access.isCurrent(arg(0), arg(1)))
            arg<(Result<EndpointDecisionSourceBinding>) -> Unit>(3)(Result.success(binding(arg(2))))
        }
        coEvery { decisions.read(any(), any(), any(), any(), any()) } coAnswers {
            assertTrue(access.isCurrent(arg(0), arg(1)))
            arg<(Result<EndpointRecoveryDecision?>) -> Unit>(4)(Result.success(null))
        }
        coEvery { decisions.readTargets(any(), any(), any(), any(), any()) } coAnswers {
            assertTrue(access.isCurrent(arg(0), arg(1)))
            arg<(Result<EndpointDecisionTargetPage>) -> Unit>(4)(Result.success(targetPage(listOf(option))))
        }
    }

    private fun callbacks(stale: SavedEndpointDecisionForm, subject: SavedEndpointsViewModel = model) {
        subject.retryDecisionLoad(stale.identity)
        subject.reloadDecisionAfterFailure(stale.identity)
        subject.changeDecisionName(stale.identity, "late name")
        subject.changeDecisionUrl(stale.identity, "wss://late.invalid")
        subject.chooseDecisionTarget(stale.identity, option)
        subject.pageDecisionTargets(stale.identity, 0)
        subject.pageDecisionTargets(stale.identity, 1)
        subject.pageDecisionTargets(stale.identity, -1)
        subject.retryDecisionTargets(stale.identity)
        subject.saveDecision(stale)
    }

    private fun unlock() {
        dispatcher.scheduler.runCurrent()
        model.unlock(); model.paused()
        assertTrue(access.approve(owner.captured, request.captured))
        model.resumed()
    }

    private fun open() {
        unlock(); dispatcher.scheduler.runCurrent()
        model.selectSource(EndpointInventorySource.OWNED); dispatcher.scheduler.runCurrent()
        model.editRecord(ref()); dispatcher.scheduler.runCurrent()
    }

    private fun newModel() = SavedEndpointsViewModel(inventory, router, access, decisions, mockk(), mockk(relaxed = true)).also {
        models += it
        stores += ViewModelStore().apply { put("model", it) }
    }

    private fun form() = requireNotNull(model.state.value.decisionForm)
    private fun ref(source: EndpointInventorySource = EndpointInventorySource.OWNED, row: Long = 7, revision: Long = 7) =
        EndpointRecordRef(source, row, revision.takeIf { source == EndpointInventorySource.OWNED })

    private fun binding(value: EndpointRecordRef): EndpointDecisionSourceBinding = mockk {
        every { ref } returns value
    }

    private fun decision(value: EndpointRecordRef, revision: Long, candidate: EndpointDecisionCandidate): EndpointRecoveryDecision {
        val capturedSource = binding(value)
        return mockk {
            every { id } returns 1L
            every { this@mockk.revision } returns revision
            every { source } returns capturedSource
            every { this@mockk.candidate } returns candidate
            every { target } returns this@SavedEndpointDecisionViewModelTest.target
            every { state } returns "PENDING"
        }
    }

    private fun option(row: Long, name: String, target: EndpointDecisionTarget?): EndpointDecisionTargetOption = mockk {
        every { rowId } returns row
        every { this@mockk.name } returns name
        every { this@mockk.target } returns target
    }

    private fun targetPage(options: List<EndpointDecisionTargetOption>, previous: Long? = null, next: Long? = null): EndpointDecisionTargetPage = mockk {
        every { this@mockk.options } returns options
        every { previousBeforeRowId } returns previous
        every { nextAfterRowId } returns next
    }

    private fun page(source: EndpointInventorySource): EndpointPage {
        val exact = ref(source)
        val text = EndpointValue.Text(4, EndpointTextState.AVAILABLE, "test")
        val fields = mutableMapOf(EndpointField.NAME to text, EndpointField.ENDPOINT to text, EndpointField.CHAIN_REFERENCE to text)
            .mapValues { it.value as EndpointValue }.toMutableMap()
        if (source != EndpointInventorySource.OWNED) {
            fields[EndpointField.IS_DEFAULT] = EndpointValue.Integer(0)
            fields[EndpointField.IS_ACTIVE] = EndpointValue.Integer(0)
        }
        return EndpointPage(source, true, if (source == EndpointInventorySource.OWNED) null else EndpointSourceProvenance(81, 81),
            listOf(EndpointRecord(exact, fields, if (source == EndpointInventorySource.OWNED) EndpointOwnership(false, false, 7) else null)), null, null)
    }

    private fun legacyRecord(row: Long): LegacyEndpointRecord {
        val text = LegacyEndpointText(LegacyEndpointStorage.TEXT, 4, LegacyEndpointTextState.AVAILABLE, "test")
        val flag = LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, 0)
        return LegacyEndpointRecord(row, text, text, flag, flag, flag)
    }
}
