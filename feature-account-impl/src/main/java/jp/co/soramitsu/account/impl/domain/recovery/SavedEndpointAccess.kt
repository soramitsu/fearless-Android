package jp.co.soramitsu.account.impl.domain.recovery

import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local ownership for one Saved endpoints PIN round-trip.
 *
 * A request token is correlation only, never authorization. The caller of
 * [approve] must have just authenticated the current PIN screen and checked
 * its exact destination, foreground lifetime, healthy PIN and non-view-only
 * access. Neither an arbitrary navigation call nor a restored token suffices.
 *
 * The destination checks its lease before reads and before publishing results.
 * It clears visible data and revokes the lease on departure. The host calls
 * [revokeAll] on background/security invalidation. Nothing is persisted.
 */
@Singleton
class SavedEndpointAccess internal constructor(private val nowMillis: () -> Long) {
    @Inject constructor() : this(SystemClock::elapsedRealtime)

    /** Correlation only; persisting this id never preserves approval or owner identity. */
    class Request internal constructor(val id: String)

    /** Opaque identity; it contains no wallet, endpoint or authentication data. */
    class Lease internal constructor() {
        private val revoked = CompletableDeferred<Unit>()

        /** Completes once this exact lease is retired; cancellation only removes this waiter. */
        internal suspend fun awaitRevocation() = revoked.await()

        /** Called after the access monitor is released, so resumed observers never run under it. */
        internal fun notifyRevoked() { revoked.complete(Unit) }
    }

    private class Pending(
        val owner: Any,
        val token: String,
        val startedAt: Long,
        var approved: Boolean = false
    )

    private class Active(
        val owner: Any,
        val lease: Lease,
        val decisionAuthority: EndpointDecisionAuthority,
        val attachmentAuthority: EndpointAttachmentAuthority,
        var selectionAuthority: EndpointSelectionAuthority? = null,
        var selectionLane: EndpointMutationLane? = null
    )

    private var pending: Pending? = null
    private var active: Active? = null

    /** Replaces every earlier pending request and active lease. */
    fun begin(owner: Any): Request {
        val (request, retired) = synchronized(this) {
            val token = UUID.randomUUID().toString()
            pending = Pending(owner, token, nowMillis())
            Request(token) to retireActive()
        }
        retired?.notifyRevoked()
        return request
    }

    /** Called once by the current successful authentication completion. */
    @Synchronized
    fun approve(owner: Any, requestToken: String): Boolean {
        val request = currentPending() ?: return false
        if (request.owner !== owner || request.token != requestToken || request.approved) return false
        request.approved = true
        return true
    }

    /** Consumes the approval once, only for the same non-restored owner. */
    fun claim(owner: Any, requestToken: String): Lease? {
        val (lease, retired) = synchronized(this) {
            val request = currentPending() ?: return null
            if (request.owner !== owner || request.token != requestToken || !request.approved) return null
            pending = null
            val previous = retireActive()
            val claimed = Lease()
            active = Active(owner, claimed, EndpointDecisionAuthority(), EndpointAttachmentAuthority())
            claimed to previous
        }
        retired?.notifyRevoked()
        return lease
    }

    /** A lease remains valid only until its explicit lifecycle revocation. */
    @Synchronized
    fun isCurrent(owner: Any, lease: Lease): Boolean =
        active?.let { it.owner === owner && it.lease === lease } == true

    /** Clears page authority while allowing the intentional PIN transition. */
    fun revokeLease(owner: Any) {
        val retired = synchronized(this) { if (active?.owner === owner) retireActive() else null }
        retired?.notifyRevoked()
    }

    /**
     * Removes authorization during key revalidation, retaining only the owner's
     * pending correlation and its original expiry. A new successful PIN completion
     * must approve again; becoming healthy alone cannot restore page access.
     */
    fun revokeAuthorization(owner: Any) {
        val retired = synchronized(this) {
            if (pending?.owner === owner) pending?.approved = false
            if (active?.owner === owner) retireActive() else null
        }
        retired?.notifyRevoked()
    }

    /** Cancels the owner's PIN request as well as any current page authority. */
    fun revoke(owner: Any) {
        val retired = synchronized(this) {
            if (pending?.owner === owner) pending = null
            if (active?.owner === owner) retireActive() else null
        }
        retired?.notifyRevoked()
    }

    /** Background, process security invalidation and host destruction revoke all. */
    fun revokeAll() {
        val retired = synchronized(this) {
            pending = null
            retireActive()
        }
        retired?.notifyRevoked()
    }

    /** Internal bridge only: a constructed or stale lease never exposes a commit mechanism. */
    @Synchronized
    internal fun decisionAuthority(owner: Any, lease: Lease): EndpointDecisionAuthority? =
        active?.takeIf { it.owner === owner && it.lease === lease }?.decisionAuthority

    /** C2 authority is distinct from offline-save grants and belongs only to this exact lease. */
    @Synchronized
    internal fun attachmentAuthority(owner: Any, lease: Lease): EndpointAttachmentAuthority? =
        active?.takeIf { it.owner === owner && it.lease === lease }?.attachmentAuthority

    /** A distinct C3 authority, bound lazily without opening storage during PIN-access construction. */
    @Synchronized
    internal fun selectionAuthority(owner: Any, lease: Lease, lane: EndpointMutationLane): EndpointSelectionAuthority? {
        val current = active?.takeIf { it.owner === owner && it.lease === lease } ?: return null
        if (current.selectionLane != null && current.selectionLane !== lane) return null
        return current.selectionAuthority ?: EndpointSelectionAuthority(lane).also {
            current.selectionLane = lane
            current.selectionAuthority = it
        }
    }

    /** Called only under this object's short lifecycle monitor; no storage or callback runs here. */
    private fun retireActive(): Lease? {
        val retired = active
        retired?.decisionAuthority?.revoke()
        retired?.attachmentAuthority?.revoke()
        retired?.selectionAuthority?.revoke()
        active = null
        return retired?.lease
    }

    private fun currentPending(): Pending? {
        val request = pending ?: return null
        val age = nowMillis() - request.startedAt
        if (age !in 0 until REQUEST_LIFETIME_MILLIS) {
            pending = null
            return null
        }
        return request
    }

    private companion object {
        const val REQUEST_LIFETIME_MILLIS = 5 * 60 * 1000L
    }
}
