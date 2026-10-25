package jp.co.soramitsu.account.impl.domain.recovery

import javax.inject.Inject
import jp.co.soramitsu.common.data.network.endpoint.EndpointGenesisProbe
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeFamily
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeOutcome
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeRequest
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentResult
import jp.co.soramitsu.coredb.recovery.decision.OrderedEndpointAttachmentStore
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Explicit verification and inactive attachment under the current saved-endpoints PIN lease. */
class SavedEndpointAttachments internal constructor(
    private val access: SavedEndpointAccess,
    private val storage: Storage,
    private val lane: EndpointMutationLane,
    private val probe: suspend (EndpointProbeRequest) -> EndpointProbeOutcome
) {
    @Inject constructor(database: AppDatabase, access: SavedEndpointAccess) :
        this(access, SqlStorage(database), database.chainDao().endpointMutationLane, EndpointGenesisProbe()::probe)

    /** Called by an explicit main-thread action, never by load/save/resume. Performs no IO. */
    internal fun newOperation(
        owner: Any,
        lease: SavedEndpointAccess.Lease,
        ref: EndpointRecordRef,
        decisionId: Long,
        decisionRevision: Long,
        chainId: String,
        action: EndpointAttachmentAction
    ): SavedEndpointAttachmentOperation? {
        val authority = access.attachmentAuthority(owner, lease)?.takeIf { it.isCurrent() } ?: return null
        return SavedEndpointAttachmentOperation(owner, lease, authority, ref, decisionId, decisionRevision, action,
            lane, lane.admit(listOf(chainId)))
    }

    /** Results publish synchronously on Main only to this exact still-current lease and operation. */
    internal suspend fun execute(
        operation: SavedEndpointAttachmentOperation,
        publish: (Result<SavedEndpointAttachmentOutcome>) -> Unit
    ) {
        val admitted = withContext(Dispatchers.Main.immediate) {
            if (!current(operation)) { operation.cancel(); false } else operation.begin()
        }
        if (!admitted) return
        try {
            val result = try {
                Result.success(perform(operation))
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                if (current(operation) && operation.grantPublication()) publish(result)
            }
        } finally {
            operation.cancel()
        }
    }

    private fun current(operation: SavedEndpointAttachmentOperation): Boolean =
        access.attachmentAuthority(operation.owner, operation.lease) === operation.authority &&
            operation.authority.isCurrent() && lane.isLatest(operation.mutation)

    private suspend fun perform(operation: SavedEndpointAttachmentOperation): SavedEndpointAttachmentOutcome {
        val capture = storage.prepare(operation)
        currentCoroutineContext().ensureActive()
        val authority = operation.authority
        val attempt = operation.bind(capture)
        if (capture.alreadyPresent) {
            return SavedEndpointAttachmentOutcome.Stored(storage.observe(capture, authority, attempt))
        }
        val decision = capture.decision
        val family = when (decision.target.family) {
            EndpointDecisionTarget.Family.SUBSTRATE -> EndpointProbeFamily.SUBSTRATE
            EndpointDecisionTarget.Family.SOLANA -> EndpointProbeFamily.SOLANA
            EndpointDecisionTarget.Family.UNSUPPORTED -> error("Endpoint target has no verification protocol")
        }
        // Both URL and trusted identity come from the same revalidated saved capture.
        val request = EndpointProbeRequest(family, decision.candidate.url, checkNotNull(decision.target.expectedGenesis))
        var observed: EndpointProbeOutcome? = null
        val matched = authority.requestOnce(attempt) {
            currentCoroutineContext().ensureActive()
            probe(request).also { observed = it } == EndpointProbeOutcome.MATCH
        }
        currentCoroutineContext().ensureActive()
        if (!matched) return SavedEndpointAttachmentOutcome.Rejected(checkNotNull(observed))
        return SavedEndpointAttachmentOutcome.Stored(storage.commit(capture, authority, attempt))
    }

    /** Test seam around actual SQL, with no authority fabrication in the production implementation. */
    internal interface Storage {
        suspend fun prepare(operation: SavedEndpointAttachmentOperation): EndpointAttachmentCapture
        suspend fun observe(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult
        suspend fun commit(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult
    }

    private class SqlStorage(private val database: AppDatabase) : Storage {
        override suspend fun prepare(operation: SavedEndpointAttachmentOperation): EndpointAttachmentCapture =
            withSavedEndpointMutationDeadline { signal ->
                OrderedEndpointAttachmentStore(database.openHelper.writableDatabase, database.chainDao().endpointMutationLane).prepare(
                    signal, operation.mutation, operation.ref, operation.decisionId, operation.decisionRevision, operation.action
                )
            }

        override suspend fun observe(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult = withSavedEndpointMutationDeadline { signal ->
            OrderedEndpointAttachmentStore(database.openHelper.writableDatabase, database.chainDao().endpointMutationLane).observePresent(signal, capture, authority, attempt)
        }

        override suspend fun commit(
            capture: EndpointAttachmentCapture, authority: EndpointAttachmentAuthority, attempt: EndpointAttachmentAuthority.Attempt
        ): EndpointAttachmentResult = withSavedEndpointMutationDeadline { signal ->
            OrderedEndpointAttachmentStore(database.openHelper.writableDatabase, database.chainDao().endpointMutationLane).commitMatched(signal, capture, authority, attempt)
        }
    }
}
