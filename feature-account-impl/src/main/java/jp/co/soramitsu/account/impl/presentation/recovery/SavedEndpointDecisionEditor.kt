package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAttachmentOperation
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAttachmentOutcome
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAttachments
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentResult
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointSelections
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointDecisions
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionCandidate
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionSourceBinding
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTargetOption
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Main-thread editor for an already admitted listed record. The destination owns
 * PIN health, list membership and lifecycle; the adapter rechecks the exact lease
 * before IO and synchronous publication. Each form and operation has a new identity.
 */
internal class SavedEndpointDecisionEditor(
    private val owner: Any,
    private val operations: SavedEndpointDecisions,
    private val scope: CoroutineScope,
    private val currentLease: () -> SavedEndpointAccess.Lease?,
    private val publish: (SavedEndpointDecisionForm?) -> Unit,
    private val attachments: SavedEndpointAttachments,
    selections: SavedEndpointSelections? = null
) {
    private var form: SavedEndpointDecisionForm? = null
    private var lease: SavedEndpointAccess.Lease? = null
    private var source: EndpointDecisionSourceBinding? = null
    private var expectedRevision: Long? = null
    private var savedDecision: EndpointRecoveryDecision? = null
    private var attachmentOperation: SavedEndpointAttachmentOperation? = null
    private var generation = Any()
    private var job: Job? = null
    private var preserveEditsOnLoad = false
    private var targetAfter: Long? = null
    private var targetBefore: Long? = null

    private val selectionEditor = selections?.let { adapter ->
        SavedEndpointSelectionEditor(owner, adapter, scope, currentLease) { selection ->
            form?.let { update(it.copy(selection = selection)) }
        }
    }

    /** Start with empty candidate text; raw archive previews are never candidates. */
    fun open(ref: EndpointRecordRef) {
        clear()
        lease = currentLease() ?: return
        update(SavedEndpointDecisionForm(Any(), ref))
        loadInitial()
    }

    /** Retry failed initial loading; successful edits cannot be silently reloaded. */
    fun retryLoad(identity: Any) {
        val current = admitted(identity) ?: return
        if (current.loading || current.attaching || current.attachmentRequiresRefresh || !current.loadFailed) return
        loadInitial(preserveEditsOnLoad)
    }

    /** Explicitly refresh source/revision after a failed save while preserving every edit. */
    fun reloadAfterFailure(identity: Any) {
        val current = admitted(identity) ?: return
        if (current.loading || current.saving || current.attaching || current.attachmentRequiresRefresh || !current.saveFailed) return
        source = null
        loadInitial(preserveEdits = true)
    }

    /** Reject invalid or oversized UTF-8 without trimming, replacing or truncating input. */
    fun changeText(identity: Any, name: String? = null, url: String? = null) {
        val current = admitted(identity) ?: return
        if (current.loading || current.loadFailed || current.saving || current.attaching || current.attachmentRequiresRefresh) return
        selectionEditor?.clear()
        val newName = name ?: current.name
        val newUrl = url ?: current.url
        try {
            // Empty URL is allowed while editing, but not when saving.
            EndpointDecisionCandidate(newName, newUrl.ifEmpty { "x" })
        } catch (_: IllegalArgumentException) {
            update(current.copy(inputRejected = true))
            return
        }
        savedDecision = null
        update(current.copy(name = newName, url = newUrl, inputRejected = false, saved = false, saveFailed = false,
            attachmentReady = false, attachmentStatus = null))
    }

    /** Only an exact option from the currently displayed page can change the target. */
    fun chooseTarget(identity: Any, option: EndpointDecisionTargetOption) {
        val current = admitted(identity) ?: return
        if (current.loading || current.loadFailed || current.saving || current.attaching || current.attachmentRequiresRefresh || current.targetsLoading ||
            !current.choosingTarget || current.targets?.options?.none { it === option } != false || option.target == null) return
        selectionEditor?.clear()
        savedDecision = null
        update(current.copy(target = option.target, targetName = option.name, choosingTarget = false, saved = false, saveFailed = false,
            attachmentReady = false, attachmentStatus = null))
    }

    /** Page in one direction; zero refreshes the catalog without replacing typed edits. */
    fun pageTargets(identity: Any, direction: Int) {
        require(direction in -1..1)
        val current = admitted(identity) ?: return
        if (current.loading || current.loadFailed || current.saving || current.attaching || current.attachmentRequiresRefresh || current.targetsLoading) return
        if (direction != 0 && !current.choosingTarget) return
        val boundary = when (direction) {
            -1 -> current.targets?.previousBeforeRowId ?: return
            1 -> current.targets?.nextAfterRowId ?: return
            else -> null
        }
        update(current.copy(choosingTarget = true))
        loadTargets(if (direction == 1) boundary else null, if (direction == -1) boundary else null)
    }

    /** Retry only the failed catalog boundary, keeping bounded edits intact. */
    fun retryTargets(identity: Any) {
        val current = admitted(identity) ?: return
        if (current.loading || current.loadFailed || !current.targetsFailed || current.targetsLoading || current.saving || current.attaching || current.attachmentRequiresRefresh) return
        loadTargets(targetAfter, targetBefore)
    }

    /** Save precisely the displayed snapshot; stale buttons cannot save newer edits. */
    fun save(expected: SavedEndpointDecisionForm) {
        val current = admitted(expected.identity) ?: return
        if (current !== expected || current.loading || current.loadFailed || current.saving || current.attaching || current.attachmentRequiresRefresh || current.targetsLoading) return
        val binding = source ?: return
        val target = current.target ?: return
        val candidate = try {
            EndpointDecisionCandidate(current.name, current.url)
        } catch (_: IllegalArgumentException) {
            update(current.copy(inputRejected = true))
            return
        }
        val activeLease = checkNotNull(lease)
        val revision = expectedRevision
        val token = retireJob()
        savedDecision = null
        update(current.copy(saving = true, saveFailed = false, saved = false, inputRejected = false,
            attachmentReady = false, attachmentStatus = null))
        launchJob {
            try {
                val result = received<EndpointRecoveryDecision> { callback ->
                    operations.saveOffline(owner, activeLease, binding, candidate, target, revision, callback)
                }
                if (isCurrent(token, current.identity)) {
                    check(result.source.ref == current.ref && result.candidate.name == candidate.name &&
                        result.candidate.url == candidate.url && result.target.chainId == target.chainId)
                    expectedRevision = result.revision
                    savedDecision = result
                    update(checkNotNull(form).copy(saving = false, saved = true, attachmentReady = true, attachmentSourceChanged = false))
                    refreshSelection()
                }
            } catch (canceled: CancellationException) {
                if (token === generation && currentLease() !== lease) clear()
                throw canceled
            } catch (_: Exception) {
                if (isCurrent(token, current.identity)) update(checkNotNull(form).copy(saving = false, saveFailed = true))
            }
        }
    }

    /** Verify only this exact saved form on an explicit click; attachment never selects a node. */
    fun attach(expected: SavedEndpointDecisionForm, action: EndpointAttachmentAction = EndpointAttachmentAction.ADD_NEW) {
        val current = admitted(expected.identity) ?: return
        if (current !== expected || current.loading || current.loadFailed || current.saving || current.attaching ||
            current.attachmentRequiresRefresh || current.targetsLoading || !current.attachmentReady) return
        val decision = savedDecision ?: return
        if (decision.source.ref != current.ref || decision.candidate.name != current.name ||
            decision.candidate.url != current.url || decision.target !== current.target) return
        val activeLease = checkNotNull(lease)
        if (action == EndpointAttachmentAction.REATTACH_EXACT_OWNED && current.ref.source != EndpointInventorySource.OWNED) return
        val operation = attachments.newOperation(owner, activeLease, current.ref, decision.id, decision.revision, decision.target.chainId, action)
            ?: return clear()
        val token = retireJob()
        attachmentOperation = operation
        update(current.copy(attaching = true, attachmentStatus = null))
        launchJob {
            try {
                val outcome = received<SavedEndpointAttachmentOutcome> { callback -> attachments.execute(operation, callback) }
                if (isCurrent(token, current.identity)) {
                    val stored = (outcome as? SavedEndpointAttachmentOutcome.Stored)?.result
                    val refresh = stored?.requiresSourceRefresh == true
                    update(checkNotNull(form).copy(attaching = false, attachmentStatus = when (outcome) {
                        is SavedEndpointAttachmentOutcome.Rejected -> SavedEndpointAttachmentStatus.fromProbe(outcome.reason)
                        is SavedEndpointAttachmentOutcome.Stored -> when (outcome.result.kind) {
                            EndpointAttachmentResult.Kind.ADDED_INACTIVE -> SavedEndpointAttachmentStatus.ADDED_INACTIVE
                            EndpointAttachmentResult.Kind.REATTACHED_INACTIVE -> SavedEndpointAttachmentStatus.REATTACHED_INACTIVE
                            EndpointAttachmentResult.Kind.ALREADY_PRESENT -> if (outcome.result.isActive)
                                SavedEndpointAttachmentStatus.PRESENT_ACTIVE else SavedEndpointAttachmentStatus.PRESENT_INACTIVE
                        }
                    }, attachmentRequiresRefresh = refresh, attachmentReady = !refresh))
                    if (refresh) savedDecision = null else refreshSelection()
                }
            } catch (canceled: CancellationException) {
                if (token === generation && currentLease() !== lease) clear()
                throw canceled
            } catch (_: Exception) {
                if (isCurrent(token, current.identity)) update(checkNotNull(form).copy(
                    attaching = false, attachmentStatus = SavedEndpointAttachmentStatus.FAILED))
            }
        }
    }

    /** An explicit Select uses the exact form and its separately rendered owner observation. */
    fun select(expected: SavedEndpointDecisionForm) {
        val current = admitted(expected.identity) ?: return
        if (current !== expected || !selectionAllowed(current)) return
        current.selection?.let { selectionEditor?.select(it) }
    }

    /** Explicit reread after a stale/failed selection never saves, attaches or starts transport. */
    fun refreshSelection(expected: SavedEndpointDecisionForm) {
        val current = admitted(expected.identity) ?: return
        if (current !== expected || !selectionAllowed(current)) return
        refreshSelection()
    }

    private fun selectionAllowed(current: SavedEndpointDecisionForm): Boolean {
        val decision = savedDecision ?: return false
        return !current.loading && !current.loadFailed && !current.saving && !current.attaching &&
            !current.attachmentRequiresRefresh && !current.targetsLoading &&
            decision.source.ref == current.ref && decision.candidate.name == current.name &&
            decision.candidate.url == current.url && decision.target === current.target
    }

    private fun refreshSelection() {
        val current = form ?: return
        val decision = savedDecision ?: return
        if (selectionAllowed(current)) selectionEditor?.open(decision)
    }

    /** Cancel work and synchronously remove all form text, bindings and catalog rows. */
    fun clear() {
        retireJob()
        lease = null
        source = null
        expectedRevision = null
        savedDecision = null
        preserveEditsOnLoad = false
        targetAfter = null
        targetBefore = null
        update(null)
    }

    private fun loadInitial(preserveEdits: Boolean = false) {
        preserveEditsOnLoad = preserveEdits
        savedDecision = null
        val current = form ?: return
        val activeLease = lease ?: return
        val token = retireJob()
        update(current.copy(loading = true, loadFailed = false, attachmentReady = false, attachmentSourceChanged = false, attachmentStatus = null))
        launchJob {
            try {
                val binding = received<EndpointDecisionSourceBinding> { callback ->
                    operations.prepareSource(owner, activeLease, current.ref, callback)
                }
                check(binding.ref == current.ref)
                if (!isCurrent(token, current.identity)) return@launchJob
                val decision = received<EndpointRecoveryDecision?> { callback ->
                    operations.read(owner, activeLease, current.ref.source, current.ref.rowId, callback)
                }
                check(decision == null || (decision.source.ref.source == current.ref.source &&
                    decision.source.ref.rowId == current.ref.rowId))
                if (isCurrent(token, current.identity)) {
                    source = binding
                    expectedRevision = decision?.revision
                    val sourceMatches = decision != null && (decision.source === binding || decision.source.matches(binding))
                    savedDecision = decision.takeIf { sourceMatches && !preserveEdits }
                    update(current.copy(name = if (preserveEdits) current.name else decision?.candidate?.name.orEmpty(),
                        url = if (preserveEdits) current.url else decision?.candidate?.url.orEmpty(),
                        target = if (preserveEdits) current.target else decision?.target,
                        choosingTarget = if (preserveEdits) current.choosingTarget else decision?.target == null,
                        loading = false, loadFailed = false, saveFailed = false, saved = false,
                        attachmentReady = savedDecision != null, attachmentSourceChanged = decision != null && !sourceMatches,
                        attachmentStatus = null))
                    loadTargets(null, null)
                }
            } catch (canceled: CancellationException) {
                if (token === generation && currentLease() !== lease) clear()
                throw canceled
            } catch (_: Exception) {
                if (isCurrent(token, current.identity)) update(current.copy(loading = false, loadFailed = true))
            }
        }
    }

    private fun loadTargets(after: Long?, before: Long?) {
        val current = form ?: return
        val activeLease = lease ?: return
        val token = retireJob()
        targetAfter = after
        targetBefore = before
        update(current.copy(targets = null, targetsLoading = true, targetsFailed = false))
        launchJob {
            try {
                val page = received { callback -> operations.readTargets(owner, activeLease, after, before, callback) }
                if (isCurrent(token, current.identity)) {
                    update(checkNotNull(form).copy(targets = page, targetsLoading = false))
                    refreshSelection()
                }
            } catch (canceled: CancellationException) {
                if (token === generation && currentLease() !== lease) clear()
                throw canceled
            } catch (_: Exception) {
                if (isCurrent(token, current.identity)) update(checkNotNull(form).copy(targetsLoading = false, targetsFailed = true))
            }
        }
    }

    private fun admitted(identity: Any): SavedEndpointDecisionForm? {
        if (lease == null || currentLease() !== lease) {
            clear()
            return null
        }
        return form?.takeIf { it.identity === identity }
    }

    private fun isCurrent(token: Any, identity: Any): Boolean = token === generation && admitted(identity) != null

    private fun retireJob(): Any {
        selectionEditor?.clear()
        generation = Any()
        attachmentOperation?.cancel()
        attachmentOperation = null
        job?.cancel()
        job = null
        return generation
    }

    private fun launchJob(work: suspend () -> Unit) {
        val pending = scope.launch(start = CoroutineStart.LAZY) { work() }
        job = pending
        pending.start()
    }

    private fun update(value: SavedEndpointDecisionForm?) {
        val currentSelection = selectionEditor?.state
        val rendered = if (value == null || value.selection === currentSelection) value else value.copy(selection = currentSelection)
        form = rendered
        publish(rendered)
    }

    private suspend fun <T> received(work: suspend ((Result<T>) -> Unit) -> Unit): T {
        var result: Result<T>? = null
        work { result = it }
        currentCoroutineContext().ensureActive()
        return (result ?: throw CancellationException("Endpoint editing lease retired")).getOrThrow()
    }
}
