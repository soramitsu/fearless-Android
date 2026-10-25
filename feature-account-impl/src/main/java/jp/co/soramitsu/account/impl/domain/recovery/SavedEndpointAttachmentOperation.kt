package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentCapture
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/**
 * One explicit action, bound to one owner, PIN lease and exact saved intent.
 * The UI must cancel synchronously on replacement/departure, before cancelling its job.
 * Cancellation fences future request/commit grants even while a worker is blocked.
 */
internal class SavedEndpointAttachmentOperation internal constructor(
    internal val owner: Any,
    internal val lease: SavedEndpointAccess.Lease,
    internal val authority: EndpointAttachmentAuthority,
    internal val ref: EndpointRecordRef,
    internal val decisionId: Long,
    internal val decisionRevision: Long,
    internal val action: EndpointAttachmentAction,
    private val lane: EndpointMutationLane,
    internal val mutation: EndpointMutationLane.Operation
) {
    private var active = true
    private var started = false
    private var published = false
    private var attempt: EndpointAttachmentAuthority.Attempt? = null

    init { require(decisionId > 0 && decisionRevision > 0) }

    /** Nonblocking retirement; a callback granted earlier may still finish. */
    fun cancel() = synchronized(this) {
        active = false
        attempt?.let(authority::abandon)
        lane.abandon(mutation)
    }

    internal fun begin(): Boolean = synchronized(this) {
        if (!active || started) false else { started = true; true }
    }

    /** Operation-to-authority lock order only; neither monitor encloses work or publication. */
    internal fun bind(capture: EndpointAttachmentCapture): EndpointAttachmentAuthority.Attempt = synchronized(this) {
        check(active && started && attempt == null) { "Endpoint attachment operation retired or consumed" }
        authority.newAttempt(capture).also { attempt = it }
    }

    /** Main-thread publication gets one grant; callers must also check the exact current PIN lease. */
    internal fun grantPublication(): Boolean = synchronized(this) {
        if (!active || !started || published) false else { published = true; true }
    }

    override fun toString(): String = "SavedEndpointAttachmentOperation"
}
