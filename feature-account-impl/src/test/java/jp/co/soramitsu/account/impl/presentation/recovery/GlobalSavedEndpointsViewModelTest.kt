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

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSavedEndpointsViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val inventory = mockk<SavedEndpointInventory>()
    private val decisions = mockk<SavedEndpointDecisions>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val access = SavedEndpointAccess { 0L }
    private val health = MutableStateFlow<PinHealth?>(PinHealth.HEALTHY)
    private val owner = slot<Any>()
    private val request = slot<String>()
    private val legacy = LegacyEndpointPage(false, emptyList(), null, null)
    private val models = mutableListOf<SavedEndpointsViewModel>()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var model: SavedEndpointsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        WalletViewOnlyAccess.pinResetCompleted()
        mockkObject(WalletPinHealth)
        every { WalletPinHealth.health } returns health
        every { router.requestSavedEndpointUnlock(capture(owner), capture(request)) } returns true
        coEvery { inventory.readPage(null, null) } returns legacy
        model = createModel()
    }

    @After fun teardown() {
        stores.forEach { it.clear() }
        models.forEach { it.viewModelScope.cancel(); it.coroutineContext.cancel() }
        access.revokeAll()
        WalletViewOnlyAccess.pinResetCompleted()
        unmockkObject(WalletPinHealth)
        Dispatchers.resetMain()
    }

    @Test fun lockedSourceAndRecordCallbacksNeverReadTheDatabase() = runTest(dispatcher) {
        model.selectSource(EndpointInventorySource.OWNED)
        model.inspectRecord(ref(), EndpointField.NAME)
        model.retryRecordInspection(); model.previousRecordInspection(); model.nextRecordInspection()
        runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        coVerify(exactly = 0) { inventory.readInventoryPage(any(), any(), any()) }
        coVerify(exactly = 0) { inventory.readInventoryChunk(any(), any(), any()) }
    }

    @Test fun switchingSourceClearsPriorDataBeforeReadAndFailureCannotRestoreIt() = runTest(dispatcher) {
        unlock(); runCurrent()
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } throws IllegalStateException("private")
        model.selectSource(EndpointInventorySource.OWNED)
        assertNull(model.state.value.page); assertNull(model.state.value.inventoryPage)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertTrue(model.state.value.readFailed)
        assertEquals(EndpointInventorySource.OWNED, model.state.value.source)
        assertNull(model.state.value.page); assertNull(model.state.value.inventoryPage)
        val fresh = page()
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } returns fresh
        model.retry(); runCurrent()
        assertEquals(fresh, model.state.value.inventoryPage)
        assertFalse(model.state.value.readFailed)
    }

    @Test fun sourceSwitchRetiresAnUncooperativeLegacyPage() = runTest(dispatcher) {
        unlock(); runCurrent()
        val held = CompletableDeferred<LegacyEndpointPage>()
        coEvery { inventory.readPage(null, null) } coAnswers { withContext(NonCancellable) { held.await() } }
        model.refresh(); runCurrent()
        val current = page(EndpointInventorySource.CURRENT)
        coEvery { inventory.readInventoryPage(EndpointInventorySource.CURRENT, null, null) } returns current
        model.selectSource(EndpointInventorySource.CURRENT); runCurrent()
        held.complete(legacy); runCurrent()
        assertEquals(current, model.state.value.inventoryPage)
        assertNull(model.state.value.page)
        assertEquals(EndpointInventorySource.CURRENT, model.state.value.source)
    }

    @Test fun globalPagingUsesOnlyTheSelectedSourceAndExactBoundary() = runTest(dispatcher) {
        unlock(); runCurrent()
        val first = page().copy(nextAfterRowId = 7)
        val second = page(row = 8).copy(previousBeforeRowId = 8)
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } returns first
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, 7, null) } returns second
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, 8) } returns first
        model.selectSource(EndpointInventorySource.OWNED); runCurrent()
        model.next(); model.next(); model.previous(); runCurrent()
        assertEquals(second, model.state.value.inventoryPage)
        model.previous(); runCurrent()
        assertEquals(first, model.state.value.inventoryPage)
        coVerify(exactly = 1) { inventory.readInventoryPage(EndpointInventorySource.OWNED, 7, null) }
        coVerify(exactly = 1) { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, 8) }
        coVerify(exactly = 1) { inventory.readPage(any(), any()) }
    }

    @Test fun aWrongSourceResponseIsNeverPublished() = runTest(dispatcher) {
        unlock(); runCurrent()
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } returns page(EndpointInventorySource.CURRENT)
        model.selectSource(EndpointInventorySource.OWNED); runCurrent()
        assertTrue(model.state.value.readFailed)
        assertNull(model.state.value.inventoryPage); assertNull(model.state.value.page)
        assertEquals(EndpointInventorySource.OWNED, model.state.value.source)
    }

    @Test fun inspectionRequiresTheListedSourceRowAndRevision() = runTest(dispatcher) {
        showOwned()
        model.inspectRecord(ref(revision = 2), EndpointField.NAME)
        model.inspectRecord(ref(row = 8), EndpointField.NAME)
        model.inspectRecord(EndpointRecordRef(EndpointInventorySource.CURRENT, 7), EndpointField.NAME)
        runCurrent()
        coVerify(exactly = 0) { inventory.readInventoryChunk(any(), any(), any()) }
        val response = chunk()
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns response
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        assertSame(response, model.state.value.inventoryInspection?.chunk)
        assertEquals(ref(), model.state.value.inventoryInspection?.ref)
    }

    @Test fun recordPartsStayAtOneRevisionAndRetryOnlyTheFailedOffset() = runTest(dispatcher) {
        showOwned()
        val first = chunk(next = 512)
        val second = chunk(offset = 512, previous = 0)
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns first
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 512) } throws IllegalStateException("private")
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        model.nextRecordInspection(); runCurrent()
        assertTrue(model.state.value.inventoryInspection!!.readFailed)
        assertNull(model.state.value.inventoryInspection!!.chunk)
        assertEquals(512L, model.state.value.inventoryInspection!!.byteOffset)
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 512) } returns second
        model.retryRecordInspection(); runCurrent()
        assertSame(second, model.state.value.inventoryInspection!!.chunk)
        model.previousRecordInspection(); runCurrent()
        assertSame(first, model.state.value.inventoryInspection!!.chunk)
        coVerify(exactly = 2) { inventory.readInventoryChunk(ref(), EndpointField.NAME, 512) }
        coVerify(exactly = 2) { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) }
    }

    @Test fun wrongChunkRevisionCannotReplaceTheSelectedPart() = runTest(dispatcher) {
        showOwned()
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns chunk(record = ref(revision = 2))
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        assertTrue(model.state.value.inventoryInspection!!.readFailed)
        assertNull(model.state.value.inventoryInspection!!.chunk)
        assertEquals(ref(), model.state.value.inventoryInspection!!.ref)
    }

    @Test fun sourceChangeClearsAndFencesAnUncooperativeChunk() = runTest(dispatcher) {
        showOwned()
        val held = CompletableDeferred<EndpointValueChunk>()
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } coAnswers { withContext(NonCancellable) { held.await() } }
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        val current = page(EndpointInventorySource.CURRENT)
        coEvery { inventory.readInventoryPage(EndpointInventorySource.CURRENT, null, null) } returns current
        model.selectSource(EndpointInventorySource.CURRENT)
        assertNull(model.state.value.inventoryInspection)
        runCurrent(); held.complete(chunk()); runCurrent()
        assertEquals(current, model.state.value.inventoryPage)
        assertNull(model.state.value.inventoryInspection)
    }

    @Test fun pauseAndNullPinHealthRetireGlobalPayloadBeforeLateCompletion() = runTest(dispatcher) {
        showOwned()
        val held = CompletableDeferred<EndpointValueChunk>()
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } coAnswers { withContext(NonCancellable) { held.await() } }
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        health.value = null
        model.paused(); held.complete(chunk()); runCurrent()
        assertEquals(SavedEndpointsState(), model.state.value)
        model.retryRecordInspection(); model.nextRecordInspection(); model.selectSource(EndpointInventorySource.OWNED)
        runCurrent()
        coVerify(exactly = 1) { inventory.readInventoryChunk(any(), any(), any()) }
    }

    @Test fun inspectionBackRefreshesItsCollectionBeforeNavigation() = runTest(dispatcher) {
        showOwned()
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns chunk()
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        val changed = page(revision = 2)
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } returns changed
        model.back(); runCurrent()
        assertNull(model.state.value.inventoryInspection)
        assertEquals(changed, model.state.value.inventoryPage)
        verify(exactly = 0) { router.back() }
        model.back(); model.back(); runCurrent()
        verify(exactly = 1) { router.back() }
        assertEquals(SavedEndpointsState(), model.state.value)
    }

    @Test fun staleRevisionResultIsShownWithoutAutomaticRevisionRetry() = runTest(dispatcher) {
        showOwned()
        val stale = chunk()
        every { stale.state } returns EndpointChunkState.STALE_REVISION
        coEvery { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) } returns stale
        model.inspectRecord(ref(), EndpointField.NAME); runCurrent()
        assertSame(stale, model.state.value.inventoryInspection!!.chunk)
        model.nextRecordInspection(); model.previousRecordInspection(); model.retryRecordInspection(); runCurrent()
        coVerify(exactly = 1) { inventory.readInventoryChunk(ref(), EndpointField.NAME, 0) }
    }

    private fun showOwned() {
        unlock(); dispatcher.scheduler.runCurrent()
        coEvery { inventory.readInventoryPage(EndpointInventorySource.OWNED, null, null) } returns page()
        model.selectSource(EndpointInventorySource.OWNED); dispatcher.scheduler.runCurrent()
    }
    private fun unlock() {
        dispatcher.scheduler.runCurrent()
        model.unlock(); model.paused()
        assertTrue(access.approve(owner.captured, request.captured))
        model.resumed()
    }
    private fun createModel() = SavedEndpointsViewModel(inventory, router, access, decisions, mockk(), mockk(relaxed = true)).also {
        models += it; stores += ViewModelStore().apply { put("model", it) }
    }
    private fun ref(row: Long = 7, revision: Long = 1) = EndpointRecordRef(EndpointInventorySource.OWNED, row, revision)
    private fun page(source: EndpointInventorySource = EndpointInventorySource.OWNED, row: Long = 7, revision: Long = 1): EndpointPage {
        val id = EndpointRecordRef(source, row, revision.takeIf { source == EndpointInventorySource.OWNED })
        val text = EndpointValue.Text(4, EndpointTextState.AVAILABLE, "test")
        val fields = mutableMapOf<EndpointField, EndpointValue>(EndpointField.NAME to text, EndpointField.ENDPOINT to text,
            EndpointField.CHAIN_REFERENCE to text)
        if (source != EndpointInventorySource.OWNED) {
            fields[EndpointField.IS_DEFAULT] = EndpointValue.Integer(0)
            fields[EndpointField.IS_ACTIVE] = EndpointValue.Integer(0)
        }
        val ownership = if (source == EndpointInventorySource.OWNED) EndpointOwnership(false, false, 7) else null
        val provenance = if (source == EndpointInventorySource.OWNED) null else EndpointSourceProvenance(81, 81)
        return EndpointPage(source, true, provenance, listOf(EndpointRecord(id, fields, ownership)), null, null)
    }
    /** Response fencing only; core tests own the byte/metadata construction contract. */
    private fun chunk(record: EndpointRecordRef = ref(), requestedField: EndpointField = EndpointField.NAME,
                      offset: Long = 0, previous: Long? = null, next: Long? = null): EndpointValueChunk = mockk {
        every { ref } returns record
        every { field } returns requestedField
        every { byteOffset } returns offset
        every { previousByteOffset } returns previous
        every { nextByteOffset } returns next
        every { state } returns EndpointChunkState.AVAILABLE
    }
}
