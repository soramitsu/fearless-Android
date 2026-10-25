package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/** Production attachment boundary sharing the cached ChainDao writer lane with topology and selection. */
class OrderedEndpointAttachmentStore(database: SupportSQLiteDatabase, private val lane: EndpointMutationLane) {
    private val storage = EndpointAttachmentStore(database)

    suspend fun prepare(
        signal: CancellationSignal,
        operation: EndpointMutationLane.Operation,
        ref: EndpointRecordRef,
        decisionId: Long,
        decisionRevision: Long,
        action: EndpointAttachmentAction
    ): EndpointAttachmentCapture = lane.withOperation(operation) {
        val capture = storage.prepare(signal, ref, decisionId, decisionRevision, action)
        lane.requireChain(operation, capture.decision.target.chainId)
        EndpointAttachmentCapture(
            capture.decision, capture.action, capture.owner, capture.ownerOriginRowId,
            capture.projection, capture.plan, operation
        )
    }

    suspend fun observePresent(
        signal: CancellationSignal,
        capture: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt
    ): EndpointAttachmentResult {
        val operation = checkNotNull(capture.mutation) { "Attachment capture has no writer order" }
        return lane.withOperation(operation) {
            lane.requireChain(operation, capture.decision.target.chainId)
            storage.observePresentOrdered(signal, capture, authority, attempt) {
                lane.grantFinal(operation) { authority.grantObservationOnce(attempt, capture) }
            }
        }
    }

    suspend fun commitMatched(
        signal: CancellationSignal,
        capture: EndpointAttachmentCapture,
        authority: EndpointAttachmentAuthority,
        attempt: EndpointAttachmentAuthority.Attempt
    ): EndpointAttachmentResult {
        val operation = checkNotNull(capture.mutation) { "Attachment capture has no writer order" }
        return lane.withOperation(operation) {
            lane.requireChain(operation, capture.decision.target.chainId)
            storage.commitMatchedOrdered(signal, capture, authority, attempt) {
                lane.grantFinal(operation) { authority.commitOnce(attempt, capture) {} }
            }
        }
    }
}
