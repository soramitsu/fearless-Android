package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget

/** Ephemeral rendering only. No authority, URL, owner payload or state is serialized. */
class SavedEndpointSelectionState internal constructor(
    private val observedPhase: Phase,
    private val family: EndpointDecisionTarget.Family? = null,
    private val node: ChainNode? = null,
    private val runtime: EndpointRuntimeStatus? = null,
    private val current: () -> Boolean = { true }
) {
    enum class Phase { CHECKING, AVAILABLE, NOT_ATTACHED, SAVING, SAVED, FAILED, STALE }
    enum class Runtime { PREFERRED, PENDING, NOT_IN_USE, UNAVAILABLE, PAUSED, CONNECTING, CONNECTED }

    val phase: Phase get() = if (current()) observedPhase else Phase.STALE

    /** Connected requires both the current durable receipt and the exact current runtime acknowledgment. */
    val runtimeState: Runtime? get() {
        if (phase != Phase.SAVED) return null
        if (family == EndpointDecisionTarget.Family.SOLANA) return Runtime.PREFERRED
        val status = runtime ?: return Runtime.NOT_IN_USE
        val selected = node ?: return Runtime.PENDING
        if (!status.isCurrentFor(selected)) return Runtime.PENDING
        return when (status.phase) {
            EndpointRuntimeStatus.Phase.PENDING -> Runtime.PENDING
            EndpointRuntimeStatus.Phase.NOT_IN_USE -> Runtime.NOT_IN_USE
            EndpointRuntimeStatus.Phase.UNAVAILABLE -> Runtime.UNAVAILABLE
            EndpointRuntimeStatus.Phase.PAUSED -> Runtime.PAUSED
            EndpointRuntimeStatus.Phase.CONNECTING -> if (status.applied) Runtime.CONNECTING else Runtime.PENDING
            EndpointRuntimeStatus.Phase.CONNECTED -> if (status.applied) Runtime.CONNECTED else Runtime.PENDING
        }
    }

    override fun toString(): String = "SavedEndpointSelectionState($phase)"
}
