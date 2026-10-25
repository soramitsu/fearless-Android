package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAttachments
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelections
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointInventory
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetOption
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Offline paging requires a one-shot PIN grant for this exact, resumed destination instance. */
@HiltViewModel
class SavedEndpointsViewModel @Inject constructor(
    private val inventory: SavedEndpointInventory,
    private val router: AccountRouter,
    private val access: SavedEndpointAccess,
    decisions: SavedEndpointDecisions,
    attachments: SavedEndpointAttachments,
    selections: SavedEndpointSelections
) : BaseViewModel() {
    private data class PageRequest(val after: Long? = null, val before: Long? = null)
    private val owner = Any()
    private val mutableState = MutableStateFlow(SavedEndpointsState())
    val state = mutableState.asStateFlow()
    private var pendingRequest: String? = null
    private var lease: SavedEndpointAccess.Lease? = null
    private var leaseObserver: Job? = null
    private var requested = PageRequest()
    private var generation = 0L
    private var readJob: Job? = null
    private var departed = false

    private val editor = SavedEndpointDecisionEditor(owner, decisions, viewModelScope,
        { lease?.takeIf { canRead() } },
        { form -> mutableState.value = mutableState.value.copy(decisionForm = form) }, attachments, selections)

    init {
        viewModelScope.launch {
            combine(WalletPinHealth.health, WalletViewOnlyAccess.enabled) { health, viewOnly ->
                health to viewOnly
            }.collect { (health, viewOnly) ->
                when {
                    viewOnly -> lock(revokeRequest = true)
                    health == null -> {
                        // A real key read re-attests health. Keep correlation, never authority.
                        lock(revokeRequest = false)
                        access.revokeAuthorization(owner)
                    }
                    health != PinHealth.HEALTHY -> lock(revokeRequest = true)
                }
            }
        }
    }

    /** A fresh request contains no credentials and is never saved in instance state. */
    fun unlock() {
        if (departed || !mutableState.value.locked || pendingRequest != null) return
        val request = access.begin(owner)
        pendingRequest = request.id
        val started = try {
            router.requestSavedEndpointUnlock(owner, request.id)
        } catch (failure: CancellationException) {
            lock(revokeRequest = true)
            throw failure
        } catch (_: Exception) {
            false
        }
        if (!started) {
            lock(revokeRequest = true)
            mutableState.value = mutableState.value.copy(unlockFailed = true)
        }
    }

    /** Called on resume; cancellation, restoration and ordinary back navigation cannot grant access. */
    fun resumed() {
        if (departed) return
        val request = pendingRequest
        pendingRequest = null
        lease = request?.let { access.claim(owner, it) }
        if (!canRead()) {
            lock(revokeRequest = true)
            return
        }
        observeLease(checkNotNull(lease))
        if (!canRead()) return lock(revokeRequest = true)
        mutableState.value = SavedEndpointsState(locked = false)
        load(PageRequest())
    }

    /** Pausing always removes data; only the deliberate in-flight PIN request may remain pending. */
    fun paused() = lock(revokeRequest = false)

    /** Retire the old collection before any read; failures cannot expose records from another source. */
    fun selectSource(source: EndpointInventorySource) {
        if (!canRead()) return lock(revokeRequest = true)
        if (source == mutableState.value.source) return
        generation++
        readJob?.cancel()
        editor.clear()
        requested = PageRequest()
        mutableState.value = SavedEndpointsState(locked = false, source = source)
        load(requested)
    }

    fun refresh() = load(PageRequest())
    fun retry() = load(requested)
    fun previous() {
        if (mutableState.value.loading) return
        val current = mutableState.value
        val boundary = (if (current.source == EndpointInventorySource.LEGACY) current.page?.previousBeforeRowId
            else current.inventoryPage?.previousBeforeRowId) ?: return
        load(PageRequest(before = boundary))
    }
    fun next() {
        if (mutableState.value.loading) return
        val current = mutableState.value
        val boundary = (if (current.source == EndpointInventorySource.LEGACY) current.page?.nextAfterRowId
            else current.inventoryPage?.nextAfterRowId) ?: return
        load(PageRequest(after = boundary))
    }
    fun back() {
        if (departed) return
        if (mutableState.value.decisionForm != null || mutableState.value.inspection != null || mutableState.value.inventoryInspection != null) {
            returnToList()
            return
        }
        departed = true
        lock(revokeRequest = true)
        router.back()
    }

    /** Inspect only a currently listed row under this destination's active lease. */
    fun inspect(originalRowId: Long, field: LegacyEndpointField) {
        val current = mutableState.value
        if (current.source != EndpointInventorySource.LEGACY || current.loading || current.inspection != null ||
            current.inventoryInspection != null || current.decisionForm != null ||
            current.page?.records?.none { it.originalRowId == originalRowId } != false) return
        loadChunk(SavedEndpointInspectionState(originalRowId, field))
    }

    /** Inspect only an exact listed source/revision; stale callbacks cannot select another collection. */
    fun inspectRecord(ref: EndpointRecordRef, field: EndpointField) {
        val current = mutableState.value
        if (current.loading || current.inspection != null || current.inventoryInspection != null || current.decisionForm != null ||
            current.source != ref.source) return
        val listed = if (ref.source == EndpointInventorySource.LEGACY) {
            current.page?.records?.any { it.originalRowId == ref.rowId } == true
        } else current.inventoryPage?.records?.any { it.ref == ref } == true
        if (!listed) return
        loadRecordChunk(SavedEndpointRecordInspectionState(ref, field))
    }

    /** Retry only the selected source, revision and byte offset. */
    fun retryRecordInspection() {
        val current = mutableState.value.inventoryInspection ?: return
        if (current.loading || !current.readFailed) return
        loadRecordChunk(current)
    }

    /** Read the preceding bounded part without accumulating payload. */
    fun previousRecordInspection() {
        val current = mutableState.value.inventoryInspection ?: return
        if (current.loading) return
        val offset = current.chunk?.previousByteOffset ?: return
        loadRecordChunk(current.copy(byteOffset = offset))
    }

    /** Read the following bounded part at the same selected revision. */
    fun nextRecordInspection() {
        val current = mutableState.value.inventoryInspection ?: return
        if (current.loading) return
        val offset = current.chunk?.nextByteOffset ?: return
        loadRecordChunk(current.copy(byteOffset = offset))
    }

    /** Retry the exact failed part; no other row or offset is selected implicitly. */
    fun retryInspection() {
        val current = mutableState.value.inspection ?: return
        if (current.loading || !current.readFailed) return
        loadChunk(current)
    }

    /** Read the preceding bounded part without accumulating earlier payload. */
    fun previousInspection() {
        val current = mutableState.value.inspection ?: return
        if (current.loading) return
        val offset = current.chunk?.previousByteOffset ?: return
        loadChunk(current.copy(byteOffset = offset))
    }

    /** Read the next bounded part without accumulating earlier payload. */
    fun nextInspection() {
        val current = mutableState.value.inspection ?: return
        if (current.loading) return
        val offset = current.chunk?.nextByteOffset ?: return
        loadChunk(current.copy(byteOffset = offset))
    }

    /** Retire the inspected part and refresh the bounded page under the same lease. */
    fun returnToList() {
        if (mutableState.value.decisionForm == null && mutableState.value.inspection == null && mutableState.value.inventoryInspection == null) return
        editor.clear()
        mutableState.value = mutableState.value.copy(inspection = null, inventoryInspection = null)
        load(requested)
    }

    /** Admit an exact listed source/row/revision before opening an ephemeral editor. */
    fun editRecord(ref: EndpointRecordRef) {
        if (!canRead()) return lock(revokeRequest = true)
        val current = mutableState.value
        if (current.loading || current.inspection != null || current.inventoryInspection != null ||
            current.decisionForm != null || current.source != ref.source) return
        val listed = if (ref.source == EndpointInventorySource.LEGACY) {
            current.page?.records?.any { it.originalRowId == ref.rowId } == true
        } else current.inventoryPage?.records?.any { it.ref == ref } == true
        if (!listed) return
        generation++
        readJob?.cancel()
        editor.open(ref)
    }

    /** Retry only the same failed initial form load. */
    fun retryDecisionLoad(identity: Any) = editor.retryLoad(identity)

    /** Refresh a failed save binding without replacing typed edits. */
    fun reloadDecisionAfterFailure(identity: Any) = editor.reloadAfterFailure(identity)

    /** Keep exact bounded text only for the currently authenticated form. */
    fun changeDecisionName(identity: Any, value: String) = editor.changeText(identity, name = value)

    /** Keep exact bounded URL text; this action never contacts it. */
    fun changeDecisionUrl(identity: Any, value: String) = editor.changeText(identity, url = value)

    /** Choose only a row from the current bounded local catalog page. */
    fun chooseDecisionTarget(identity: Any, option: EndpointDecisionTargetOption) = editor.chooseTarget(identity, option)

    /** Page the local target list without discarding edits. */
    fun pageDecisionTargets(identity: Any, direction: Int) = editor.pageTargets(identity, direction)

    /** Retry only a failed target page. */
    fun retryDecisionTargets(identity: Any) = editor.retryTargets(identity)

    /** Persist the displayed choice as pending, without attachment or selection. */
    fun saveDecision(expected: SavedEndpointDecisionForm) = editor.save(expected)

    /** Explicitly verify and attach this exact saved form without selecting it. */
    fun attachDecision(expected: SavedEndpointDecisionForm) = editor.attach(expected)

    /** Only an explicit restore action may reattach the exact detached owned record. */
    fun restoreDecision(expected: SavedEndpointDecisionForm) = editor.attach(expected, EndpointAttachmentAction.REATTACH_EXACT_OWNED)

    fun selectEndpoint(expected: SavedEndpointDecisionForm) = editor.select(expected)

    fun refreshSelection(expected: SavedEndpointDecisionForm) = editor.refreshSelection(expected)

    private fun loadChunk(request: SavedEndpointInspectionState) {
        if (!canRead()) {
            lock(revokeRequest = true)
            return
        }
        val currentGeneration = ++generation
        readJob?.cancel()
        val pending = request.copy(chunk = null, loading = true, readFailed = false)
        mutableState.value = mutableState.value.copy(inspection = pending)
        readJob = viewModelScope.launch {
            try {
                if (!canRead()) return@launch lock(revokeRequest = true)
                val chunk = inventory.readChunk(request.originalRowId, request.field, request.byteOffset)
                check(chunk.originalRowId == request.originalRowId && chunk.field == request.field &&
                    chunk.byteOffset == request.byteOffset) { "Saved value response identity changed" }
                if (currentGeneration == generation) {
                    if (canRead()) mutableState.value = mutableState.value.copy(
                        inspection = pending.copy(chunk = chunk, loading = false)
                    ) else lock(revokeRequest = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentGeneration == generation) {
                    if (canRead()) mutableState.value = mutableState.value.copy(
                        inspection = pending.copy(loading = false, readFailed = true)
                    ) else lock(revokeRequest = true)
                }
            }
        }
    }

    private fun loadRecordChunk(request: SavedEndpointRecordInspectionState) {
        if (!canRead()) return lock(revokeRequest = true)
        if (request.ref.source != mutableState.value.source) return
        val currentGeneration = ++generation
        readJob?.cancel()
        val pending = request.copy(chunk = null, loading = true, readFailed = false)
        mutableState.value = mutableState.value.copy(inventoryInspection = pending, inspection = null)
        readJob = viewModelScope.launch {
            try {
                if (!canRead()) return@launch lock(revokeRequest = true)
                val chunk = inventory.readInventoryChunk(request.ref, request.field, request.byteOffset)
                check(chunk.ref == request.ref && chunk.field == request.field && chunk.byteOffset == request.byteOffset) {
                    "Saved value response identity changed"
                }
                if (currentGeneration == generation && mutableState.value.source == request.ref.source) {
                    if (canRead()) mutableState.value = mutableState.value.copy(
                        inventoryInspection = pending.copy(chunk = chunk, loading = false)
                    ) else lock(revokeRequest = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentGeneration == generation && mutableState.value.source == request.ref.source) {
                    if (canRead()) mutableState.value = mutableState.value.copy(
                        inventoryInspection = pending.copy(loading = false, readFailed = true)
                    ) else lock(revokeRequest = true)
                }
            }
        }
    }

    override fun onCleared() {
        departed = true
        lock(revokeRequest = true)
        super.onCleared()
    }

    private fun canRead(): Boolean = !departed && lease?.let { access.isCurrent(owner, it) } == true &&
        WalletPinHealth.health.value == PinHealth.HEALTHY && !WalletViewOnlyAccess.isEnabled()

    /** Observe only this claim; a delayed retired-lease notification cannot clear a newer claim. */
    private fun observeLease(claimed: SavedEndpointAccess.Lease) {
        leaseObserver?.cancel()
        val observer = viewModelScope.launch(start = CoroutineStart.LAZY) {
            claimed.awaitRevocation()
            if (lease === claimed) lock(revokeRequest = true)
        }
        leaseObserver = observer
        observer.start()
    }

    private fun lock(revokeRequest: Boolean) {
        leaseObserver?.cancel()
        leaseObserver = null
        generation++
        readJob?.cancel()
        readJob = null
        lease = null
        editor.clear()
        if (revokeRequest) {
            pendingRequest = null
            access.revoke(owner)
        } else access.revokeLease(owner)
        mutableState.value = SavedEndpointsState()
    }

    private fun load(request: PageRequest) {
        if (mutableState.value.decisionForm != null || mutableState.value.inspection != null || mutableState.value.inventoryInspection != null) return
        if (!canRead()) {
            lock(revokeRequest = true)
            return
        }
        val currentGeneration = ++generation
        readJob?.cancel()
        requested = request
        val source = mutableState.value.source
        mutableState.value = mutableState.value.copy(loading = true, readFailed = false)
        readJob = viewModelScope.launch {
            try {
                if (!canRead()) return@launch lock(revokeRequest = true)
                val loaded = if (source == EndpointInventorySource.LEGACY) {
                    SavedEndpointsState(page = inventory.readPage(request.after, request.before), locked = false, source = source)
                } else {
                    val page = inventory.readInventoryPage(source, request.after, request.before)
                    check(page.source == source && page.records.all { it.ref.source == source }) {
                        "Saved inventory response source changed"
                    }
                    SavedEndpointsState(locked = false, source = source, inventoryPage = page)
                }
                if (currentGeneration == generation && mutableState.value.source == source) {
                    if (canRead()) mutableState.value = loaded
                    else lock(revokeRequest = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentGeneration == generation) {
                    if (canRead()) mutableState.value = mutableState.value.copy(loading = false, readFailed = true)
                    else lock(revokeRequest = true)
                }
            }
        }
    }
}
