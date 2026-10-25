package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelStore
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointInventory
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.coredb.recovery.LegacyEndpointRecord
import jp.co.soramitsu.coredb.recovery.LegacyEndpointText
import jp.co.soramitsu.coredb.recovery.LegacyEndpointTextState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointInteger
import jp.co.soramitsu.coredb.recovery.LegacyEndpointStorage
import kotlinx.coroutines.flow.MutableStateFlow
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointValueChunk
import jp.co.soramitsu.coredb.recovery.LegacyEndpointPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedEndpointsViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val inventory = mockk<SavedEndpointInventory>()
    private val decisions = mockk<SavedEndpointDecisions>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val first = LegacyEndpointPage(true, listOf(record(0)), null, 0L)
    private val health = MutableStateFlow<PinHealth?>(PinHealth.HEALTHY)
    private val access = SavedEndpointAccess { 0L }
    private val requestOwner = slot<Any>()
    private val requestId = slot<String>()
    private val models = mutableListOf<SavedEndpointsViewModel>()
    private val stores = mutableListOf<ViewModelStore>()
    private val second = LegacyEndpointPage(true, listOf(record(1)), 1L, null)
    private lateinit var model: SavedEndpointsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { inventory.readPage(null, null) } returns first
        WalletViewOnlyAccess.pinResetCompleted()
        mockkObject(WalletPinHealth)
        every { WalletPinHealth.health } returns health
        every { router.requestSavedEndpointUnlock(capture(requestOwner), capture(requestId)) } returns true
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

    @Test fun initialLoadDoesNotNeedAnySelectedWalletOrChainAndUsesReaderCursors() = runTest(dispatcher) {
        approveAndResume()
        assertTrue(model.state.value.loading)
        runCurrent()
        assertEquals(first, model.state.value.page)
        coEvery { inventory.readPage(0L, null) } returns second
        model.next(); runCurrent()
        assertEquals(second, model.state.value.page)
        coEvery { inventory.readPage(null, 1L) } returns first
        model.previous(); runCurrent()
        assertEquals(first, model.state.value.page)
        assertFalse(model.state.value.loading)
        coVerify(exactly = 1) { inventory.readPage(0L, null) }
        coVerify(exactly = 1) { inventory.readPage(null, 1L) }
    }

    @Test fun missingAdjacentCursorsAndRepeatedPagingDuringLoadDoNotIssueExtraReads() = runTest(dispatcher) {
        approveAndResume()
        runCurrent()
        model.previous()
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(0L, null) } coAnswers { held.await() }
        model.next(); model.next(); model.previous(); runCurrent()
        coVerify(exactly = 1) { inventory.readPage(0L, null) }
        held.complete(second); runCurrent()
        model.next(); runCurrent()
        coVerify(exactly = 2) { inventory.readPage(any(), any()) }
    }

    @Test fun localReadFailureRetainsPriorPageAndRetriesTheFailedBoundary() = runTest(dispatcher) {
        approveAndResume()
        runCurrent()
        coEvery { inventory.readPage(0L, null) } throws IllegalStateException("do not expose saved endpoint data")
        model.next(); runCurrent()
        assertTrue(model.state.value.readFailed)
        assertEquals(first, model.state.value.page)
        assertFalse(model.state.value.loading)
        coEvery { inventory.readPage(0L, null) } returns second
        model.retry(); runCurrent()
        assertEquals(second, model.state.value.page)
        assertFalse(model.state.value.readFailed)
        verify(exactly = 0) { router.openMain() }
    }

    @Test fun refreshRetiresAnEarlierUncooperativePageResult() = runTest(dispatcher) {
        approveAndResume()
        runCurrent()
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(0L, null) } coAnswers { withContext(NonCancellable) { held.await() } }
        model.next(); runCurrent()
        val replacement = LegacyEndpointPage(false, emptyList(), null, null)
        coEvery { inventory.readPage(null, null) } returns replacement
        model.refresh(); runCurrent()
        held.complete(second); runCurrent()
        assertEquals(replacement, model.state.value.page)
        assertFalse(model.state.value.loading)
    }

    @Test fun backRetiresPendingResultsAndNavigatesOnce() = runTest(dispatcher) {
        approveAndResume()
        runCurrent()
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(0L, null) } coAnswers { withContext(NonCancellable) { held.await() } }
        model.next(); runCurrent()
        model.back(); model.back(); held.complete(second); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        verify(exactly = 1) { router.back() }
    }

    @Test fun absentAndPresentEmptyArchivesRemainDistinctSuccessfulResults() = runTest(dispatcher) {
        approveAndResume()
        val absent = LegacyEndpointPage(false, emptyList(), null, null)
        coEvery { inventory.readPage(null, null) } returns absent
        runCurrent()
        assertEquals(absent, model.state.value.page)
        val empty = absent.copy(archivePresent = true)
        coEvery { inventory.readPage(null, null) } returns empty
        model.refresh(); runCurrent()
        assertEquals(empty, model.state.value.page)
        assertFalse(model.state.value.readFailed)
    }
    @Test fun lockedInitialAndRestoredOwnersCannotReadOrReuseAnotherOwnersRequest() = runTest(dispatcher) {
        model.resumed(); model.refresh(); model.retry(); model.previous(); model.next(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
        model.unlock(); model.paused()
        val originalOwner = requestOwner.captured
        val originalId = requestId.captured
        assertTrue(access.approve(originalOwner, originalId))
        val recreated = newModel()
        recreated.resumed(); runCurrent()
        assertEquals(SavedEndpointsState(), recreated.state.value)
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
    }

    @Test fun pinCancellationAndWrongApprovalDoNotReadAndPermitAnotherUnlock() = runTest(dispatcher) {
        runCurrent(); model.unlock(); model.unlock()
        verify(exactly = 1) { router.requestSavedEndpointUnlock(any(), any()) }
        val cancelled = requestId.captured
        assertFalse(access.approve(Any(), cancelled))
        model.paused(); model.resumed(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        assertFalse(access.approve(requestOwner.captured, cancelled))
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
        model.unlock()
        verify(exactly = 2) { router.requestSavedEndpointUnlock(any(), any()) }
    }

    @Test fun navigationFalseAndExceptionDiscardRequestAndPermitRetry() = runTest(dispatcher) {
        runCurrent()
        every { router.requestSavedEndpointUnlock(capture(requestOwner), capture(requestId)) } returns false
        model.unlock()
        assertTrue(model.state.value.unlockFailed)
        assertFalse(access.approve(requestOwner.captured, requestId.captured))
        every { router.requestSavedEndpointUnlock(capture(requestOwner), capture(requestId)) } throws IllegalStateException("navigation unavailable")
        model.unlock()
        assertTrue(model.state.value.unlockFailed)
        assertFalse(access.approve(requestOwner.captured, requestId.captured))
        every { router.requestSavedEndpointUnlock(capture(requestOwner), capture(requestId)) } returns true
        approveAndResume(); runCurrent()
        assertEquals(first, model.state.value.page)
        assertFalse(model.state.value.unlockFailed)
    }

    @Test fun pauseClearsAlreadyVisiblePageAndCannotReuseConsumedApprovalOnResume() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        assertEquals(first, model.state.value.page)
        model.paused()
        assertEquals(SavedEndpointsState(), model.state.value)
        model.resumed(); model.refresh(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
    }

    @Test fun pauseRejectsLateSuccessfulPageFromUncooperativeRead() = runTest(dispatcher) {
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(null, null) } coAnswers { withContext(NonCancellable) { held.await() } }
        approveAndResume(); runCurrent()
        model.paused(); held.complete(first); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun pauseRejectsLateFailedPageFromUncooperativeRead() = runTest(dispatcher) {
        val held = CompletableDeferred<Unit>()
        coEvery { inventory.readPage(null, null) } coAnswers {
            withContext(NonCancellable) { held.await(); throw IllegalStateException("private endpoint must not be exposed") }
        }
        approveAndResume(); runCurrent()
        model.paused(); held.complete(Unit); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun viewOnlyTransitionImmediatelyClearsVisibleDataAndBlocksFurtherReads() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        WalletViewOnlyAccess.enter(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        model.refresh(); runCurrent()
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
    }

    @Test fun unreadablePinTransitionRevokesPendingApprovalAndVisiblePage() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        health.value = PinHealth.UNREADABLE; runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        health.value = PinHealth.HEALTHY; runCurrent()
        model.refresh(); runCurrent()
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
        model.unlock(); model.paused()
        health.value = PinHealth.ABSENT; runCurrent()
        assertFalse(access.approve(requestOwner.captured, requestId.captured))
        model.resumed(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun hostRevocationBeforeResumeAndBeforeDeliveryNeverPublishesPage() = runTest(dispatcher) {
        runCurrent(); model.unlock(); model.paused()
        assertTrue(access.approve(requestOwner.captured, requestId.captured))
        access.revokeAll(); model.resumed(); runCurrent()
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(null, null) } coAnswers { held.await() }
        approveAndResume(); runCurrent()
        access.revokeAll(); held.complete(first); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun clearedOwnerCannotStartNewRequestOrInvalidateNewerScreenLease() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        stores.first().clear()
        val old = model
        model = newModel()
        approveAndResume(); runCurrent()
        old.unlock(); old.resumed(); old.refresh(); old.back(); runCurrent()
        assertEquals(first, model.state.value.page)
        model.refresh(); runCurrent()
        assertEquals(first, model.state.value.page)
        verify(exactly = 2) { router.requestSavedEndpointUnlock(any(), any()) }
        verify(exactly = 0) { router.back() }
    }

    @Test fun temporaryKeyRevalidationKeepsOnlyThePendingPinCorrelation() = runTest(dispatcher) {
        runCurrent(); model.unlock(); model.paused()
        health.value = null; runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
        health.value = PinHealth.HEALTHY; runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        assertTrue(access.approve(requestOwner.captured, requestId.captured))
        model.resumed(); runCurrent()
        assertEquals(first, model.state.value.page)
    }

    @Test fun keyRevalidationInvalidatesPriorApprovalAndNeedsANewSuccessfulPinCompletion() = runTest(dispatcher) {
        runCurrent(); model.unlock(); model.paused()
        assertTrue(access.approve(requestOwner.captured, requestId.captured))
        health.value = null; runCurrent()
        health.value = PinHealth.HEALTHY; runCurrent()
        assertEquals(null, access.claim(requestOwner.captured, requestId.captured))
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
        assertTrue(access.approve(requestOwner.captured, requestId.captured))
        model.resumed(); runCurrent()
        assertEquals(first, model.state.value.page)
    }

    @Test fun temporaryUnknownHealthClearsAnActivePageAndNeverAutomaticallyRestoresIt() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        assertEquals(first, model.state.value.page)
        health.value = null; runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        health.value = PinHealth.HEALTHY; runCurrent()
        model.resumed(); model.refresh(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
    }

    @Test fun unknownHealthWithoutSuccessfulPinCannotRevealOnReturn() = runTest(dispatcher) {
        runCurrent(); model.unlock(); model.paused()
        health.value = null; runCurrent()
        model.resumed(); runCurrent()
        health.value = PinHealth.HEALTHY; runCurrent()
        assertFalse(access.approve(requestOwner.captured, requestId.captured))
        model.refresh(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
    }

    @Test fun failedAttestationOrViewOnlyAfterUnknownHealthRevokesThePendingRequest() = runTest(dispatcher) {
        for (outcome in listOf(PinHealth.ABSENT, PinHealth.UNREADABLE, null)) {
            health.value = PinHealth.HEALTHY
            WalletViewOnlyAccess.pinResetCompleted()
            model = newModel(); runCurrent(); model.unlock(); model.paused()
            health.value = null; runCurrent()
            if (outcome == null) WalletViewOnlyAccess.enter() else health.value = outcome
            runCurrent()
            health.value = PinHealth.HEALTHY
            WalletViewOnlyAccess.pinResetCompleted(); runCurrent()
            assertFalse(access.approve(requestOwner.captured, requestId.captured))
            model.resumed(); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 0) { inventory.readPage(any(), any()) }
    }

    @Test fun inspectionRejectsLockedMissingAndNotYetLoadedRows() = runTest(dispatcher) {
        model.inspect(0, LegacyEndpointField.NAME)
        model.retryInspection(); model.previousInspection(); model.nextInspection(); model.returnToList()
        runCurrent()
        coVerify(exactly = 0) { inventory.readChunk(any(), any(), any()) }
        approveAndResume()
        model.inspect(0, LegacyEndpointField.NAME)
        runCurrent()
        model.inspect(999, LegacyEndpointField.NAME); runCurrent()
        coVerify(exactly = 0) { inventory.readChunk(any(), any(), any()) }
        assertNull(model.state.value.inspection)
        assertEquals(first, model.state.value.page)
    }

    @Test fun inspectionBindsBothFieldsAndAdjacentOffsetsWithoutAccumulatingChunks() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        LegacyEndpointField.values().forEach { field ->
            val initial = chunk(field = field, next = 512)
            val next = chunk(field = field, offset = 512, previous = 0)
            coEvery { inventory.readChunk(0, field, 0) } returns initial
            coEvery { inventory.readChunk(0, field, 512) } returns next
            model.inspect(0, field); runCurrent()
            assertSame(initial, model.state.value.inspection?.chunk)
            assertEquals(field, model.state.value.inspection?.field)
            model.nextInspection(); runCurrent()
            assertSame(next, model.state.value.inspection?.chunk)
            assertEquals(512L, model.state.value.inspection?.byteOffset)
            model.previousInspection(); runCurrent()
            assertSame(initial, model.state.value.inspection?.chunk)
            assertEquals(0L, model.state.value.inspection?.byteOffset)
            model.returnToList(); runCurrent()
            assertNull(model.state.value.inspection)
            coVerify(exactly = 2) { inventory.readChunk(0, field, 0) }
            coVerify(exactly = 1) { inventory.readChunk(0, field, 512) }
        }
        coVerify(exactly = 3) { inventory.readPage(null, null) }
    }

    @Test fun inspectionLoadingAndMissingCursorsSuppressDuplicateRequests() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        val held = CompletableDeferred<LegacyEndpointValueChunk>()
        coEvery { inventory.readChunk(0, LegacyEndpointField.NAME, 0) } coAnswers { held.await() }
        model.inspect(0, LegacyEndpointField.NAME); runCurrent()
        model.inspect(0, LegacyEndpointField.ENDPOINT)
        model.retryInspection(); model.previousInspection(); model.nextInspection()
        model.refresh(); model.retry(); model.next(); model.previous(); runCurrent()
        coVerify(exactly = 1) { inventory.readChunk(any(), any(), any()) }
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
        held.complete(chunk(field = LegacyEndpointField.NAME)); runCurrent()
        model.previousInspection(); model.nextInspection(); model.retryInspection(); runCurrent()
        coVerify(exactly = 1) { inventory.readChunk(any(), any(), any()) }
        model.returnToList(); model.returnToList(); runCurrent()
        coVerify(exactly = 2) { inventory.readPage(any(), any()) }
    }

    @Test fun inspectionFailureRetriesOnlyTheSelectedFailedPart() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } returns chunk(next = 512)
        coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 512) } throws IllegalStateException("private endpoint failure")
        model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
        model.nextInspection(); runCurrent()
        val failed = checkNotNull(model.state.value.inspection)
        assertTrue(failed.readFailed); assertFalse(failed.loading)
        assertNull(failed.chunk); assertEquals(512L, failed.byteOffset)
        model.previousInspection(); model.nextInspection(); runCurrent()
        coVerify(exactly = 2) { inventory.readChunk(any(), any(), any()) }
        val recovered = chunk(offset = 512, previous = 0)
        coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 512) } returns recovered
        model.retryInspection(); runCurrent()
        assertSame(recovered, model.state.value.inspection?.chunk)
        assertFalse(checkNotNull(model.state.value.inspection).readFailed)
        coVerify(exactly = 2) { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 512) }
        coVerify(exactly = 1) { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) }
        verify(exactly = 0) { router.openMain() }
    }

    @Test fun inspectionBackRefreshesTheSelectedListBoundaryBeforeOrdinaryDeparture() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        coEvery { inventory.readPage(0L, null) } returns second
        model.next(); runCurrent()
        coEvery { inventory.readChunk(1, LegacyEndpointField.NAME, 0) } returns chunk(row = 1, field = LegacyEndpointField.NAME)
        model.inspect(1, LegacyEndpointField.NAME); runCurrent()
        val fresh = second.copy(records = listOf(record(2)))
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(0L, null) } coAnswers { held.await() }
        model.back(); runCurrent()
        assertNull(model.state.value.inspection)
        assertTrue(model.state.value.loading)
        verify(exactly = 0) { router.back() }
        model.returnToList(); runCurrent()
        held.complete(fresh); runCurrent()
        assertEquals(fresh, model.state.value.page)
        coVerify(exactly = 2) { inventory.readPage(0L, null) }
        model.back(); model.back(); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        verify(exactly = 1) { router.back() }
    }

    @Test fun inspectionReturnToListFencesLateUncooperativeSuccessAndFailure() = runTest(dispatcher) {
        listOf(false, true).forEach { fails ->
            model = newModel()
            coEvery { inventory.readPage(null, null) } returns first
            approveAndResume(); runCurrent()
            val held = CompletableDeferred<Unit>()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                withContext(NonCancellable) {
                    held.await()
                    if (fails) throw IllegalStateException("obsolete private value")
                    chunk()
                }
            }
            model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            val fresh = LegacyEndpointPage(true, listOf(record(7)), null, null)
            coEvery { inventory.readPage(null, null) } returns fresh
            model.returnToList(); runCurrent()
            held.complete(Unit); runCurrent()
            assertNull(model.state.value.inspection)
            assertEquals(fresh, model.state.value.page)
            assertFalse(model.state.value.readFailed)
        }
    }

    @Test fun inspectionPauseFencesLateUncooperativeSuccessAndFailure() = runTest(dispatcher) {
        listOf(false, true).forEach { fails ->
            model = newModel(); approveAndResume(); runCurrent()
            val held = CompletableDeferred<Unit>()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                withContext(NonCancellable) {
                    held.await()
                    if (fails) throw IllegalStateException("private paused value")
                    chunk()
                }
            }
            model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            model.paused(); assertEquals(SavedEndpointsState(), model.state.value)
            held.complete(Unit); runCurrent()
            model.resumed(); model.retryInspection(); model.nextInspection(); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 2) { inventory.readChunk(any(), any(), any()) }
    }

    @Test fun inspectionSecurityChangesClearStateAndFenceLateSuccessAndFailure() = runTest(dispatcher) {
        listOf("unknown", "unreadable", "viewOnly").forEach { mode ->
            listOf(false, true).forEach { fails ->
                health.value = PinHealth.HEALTHY
                WalletViewOnlyAccess.pinResetCompleted()
                model = newModel(); approveAndResume(); runCurrent()
                val held = CompletableDeferred<Unit>()
                coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                    withContext(NonCancellable) {
                        held.await()
                        if (fails) throw IllegalStateException("private invalidated value")
                        chunk()
                    }
                }
                model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
                when (mode) {
                    "unknown" -> health.value = null
                    "unreadable" -> health.value = PinHealth.UNREADABLE
                    else -> WalletViewOnlyAccess.enter()
                }
                runCurrent(); assertEquals(SavedEndpointsState(), model.state.value)
                held.complete(Unit); runCurrent()
                health.value = PinHealth.HEALTHY
                WalletViewOnlyAccess.pinResetCompleted(); runCurrent()
                model.inspect(0, LegacyEndpointField.ENDPOINT)
                model.retryInspection(); model.previousInspection(); model.nextInspection(); model.returnToList(); runCurrent()
                assertEquals(SavedEndpointsState(), model.state.value)
            }
        }
        coVerify(exactly = 6) { inventory.readChunk(any(), any(), any()) }
    }

    @Test fun inspectionHostRevocationBlocksStartAndLateSuccessOrFailure() = runTest(dispatcher) {
        approveAndResume(); runCurrent()
        access.revokeAll(); model.inspect(0, LegacyEndpointField.NAME); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readChunk(any(), any(), any()) }
        listOf(false, true).forEach { fails ->
            model = newModel(); approveAndResume(); runCurrent()
            val held = CompletableDeferred<Unit>()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                withContext(NonCancellable) {
                    held.await()
                    if (fails) throw IllegalStateException("private host-revoked value")
                    chunk()
                }
            }
            model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            access.revokeAll(); held.complete(Unit); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 2) { inventory.readChunk(any(), any(), any()) }
    }

    @Test fun inspectionRejectsWrongResponseRowFieldOrOffsetAndRetriesExactRequest() = runTest(dispatcher) {
        listOf(chunk(row = 1), chunk(field = LegacyEndpointField.NAME), chunk(offset = 512)).forEach { mismatched ->
            model = newModel(); approveAndResume(); runCurrent()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } returns mismatched
            model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            assertTrue(checkNotNull(model.state.value.inspection).readFailed)
            assertNull(model.state.value.inspection?.chunk)
            val correct = chunk()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } returns correct
            model.retryInspection(); runCurrent()
            assertSame(correct, model.state.value.inspection?.chunk)
        }
        coVerify(exactly = 6) { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) }
    }

    @Test fun inspectionOldOwnerAndDisposedCallbacksCannotAffectNewScreenAuthority() = runTest(dispatcher) {
        listOf(false, true).forEach { fails ->
            model = newModel(); approveAndResume(); runCurrent()
            val old = model
            val oldStore = stores.last()
            val held = CompletableDeferred<Unit>()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                withContext(NonCancellable) {
                    held.await()
                    if (fails) throw IllegalStateException("old owner's private value")
                    chunk()
                }
            }
            old.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            model = newModel(); approveAndResume(); runCurrent()
            held.complete(Unit); runCurrent()
            assertEquals(SavedEndpointsState(), old.state.value)
            assertEquals(first, model.state.value.page)
            oldStore.clear()
            old.inspect(0, LegacyEndpointField.NAME); old.retryInspection()
            old.previousInspection(); old.nextInspection(); old.returnToList(); old.back(); old.refresh(); runCurrent()
            assertEquals(first, model.state.value.page)
            model.refresh(); runCurrent()
            assertEquals(first, model.state.value.page)
        }
        verify(exactly = 0) { router.back() }
        coVerify(exactly = 2) { inventory.readChunk(any(), any(), any()) }
    }

    @Test fun inspectionVisibleChunkClearsOnHealthPauseAndBackgroundRevocation() = runTest(dispatcher) {
        listOf("unknown", "unreadable", "viewOnly", "pause", "background").forEach { mode ->
            health.value = PinHealth.HEALTHY
            WalletViewOnlyAccess.pinResetCompleted()
            model = newModel(); approveAndResume(); runCurrent()
            val visible = chunk()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } returns visible
            model.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            assertSame(visible, model.state.value.inspection?.chunk)
            when (mode) {
                "unknown" -> health.value = null
                "unreadable" -> health.value = PinHealth.UNREADABLE
                "viewOnly" -> WalletViewOnlyAccess.enter()
                "background" -> { access.revokeAll(); model.paused() }
                else -> model.paused()
            }
            runCurrent(); assertEquals(SavedEndpointsState(), model.state.value)
            health.value = PinHealth.HEALTHY
            WalletViewOnlyAccess.pinResetCompleted(); runCurrent()
            model.inspect(0, LegacyEndpointField.ENDPOINT); model.retryInspection(); runCurrent()
            assertEquals(SavedEndpointsState(), model.state.value)
        }
        coVerify(exactly = 5) { inventory.readChunk(any(), any(), any()) }
    }

    @Test fun inspectionClearedOwnerCannotPublishLateChunkOrRevokeNewLease() = runTest(dispatcher) {
        listOf(false, true).forEach { fails ->
            model = newModel(); approveAndResume(); runCurrent()
            val old = model
            val held = CompletableDeferred<Unit>()
            coEvery { inventory.readChunk(0, LegacyEndpointField.ENDPOINT, 0) } coAnswers {
                withContext(NonCancellable) {
                    held.await()
                    if (fails) throw IllegalStateException("disposed owner's private value")
                    chunk()
                }
            }
            old.inspect(0, LegacyEndpointField.ENDPOINT); runCurrent()
            stores.last().clear()
            model = newModel(); approveAndResume(); runCurrent()
            held.complete(Unit); runCurrent()
            old.inspect(0, LegacyEndpointField.NAME); old.retryInspection(); old.previousInspection()
            old.nextInspection(); old.returnToList(); old.back(); runCurrent()
            assertEquals(SavedEndpointsState(), old.state.value)
            assertEquals(first, model.state.value.page)
            model.refresh(); runCurrent()
            assertEquals(first, model.state.value.page)
        }
        verify(exactly = 0) { router.back() }
        coVerify(exactly = 2) { inventory.readChunk(any(), any(), any()) }
    }

    /** Only response identity/cursors are mocked; exact-byte and construction contracts belong to core-db tests. */
    private fun chunk(
        row: Long = 0,
        field: LegacyEndpointField = LegacyEndpointField.ENDPOINT,
        offset: Long = 0,
        previous: Long? = null,
        next: Long? = null
    ): LegacyEndpointValueChunk {
        val response = mockk<LegacyEndpointValueChunk>()
        every { response.originalRowId } returns row
        every { response.field } returns field
        every { response.byteOffset } returns offset
        every { response.previousByteOffset } returns previous
        every { response.nextByteOffset } returns next
        every { response.state } returns LegacyEndpointChunkState.AVAILABLE
        return response
    }

    private fun approveAndResume() {
        dispatcher.scheduler.runCurrent()
        model.unlock()
        model.paused()
        assertTrue(access.approve(requestOwner.captured, requestId.captured))
        model.resumed()
    }

    private fun newModel(): SavedEndpointsViewModel = SavedEndpointsViewModel(inventory, router, access, decisions, mockk(), mockk(relaxed = true)).also {
        models += it
        stores += ViewModelStore().apply { put("model", it) }
    }

    private fun record(id: Long): LegacyEndpointRecord {
        val name = LegacyEndpointText(LegacyEndpointStorage.TEXT, 4, LegacyEndpointTextState.AVAILABLE, "test")
        val endpoint = LegacyEndpointText(LegacyEndpointStorage.TEXT, 21, LegacyEndpointTextState.AVAILABLE, "wss://example.invalid")
        val flag = LegacyEndpointInteger(LegacyEndpointStorage.INTEGER, null, 0)
        return LegacyEndpointRecord(id, name, endpoint, flag, flag, flag)
    }

}
