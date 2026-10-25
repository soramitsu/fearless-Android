package jp.co.soramitsu.core.runtime

import jp.co.soramitsu.core.models.ChainNode

/** A runtime observation, not a database acknowledgment, probe result, or authentication grant. */
class EndpointRuntimeStatus internal constructor(
    private val observedPhase: Phase,
    private val observedApplied: Boolean,
    private val node: ChainNode?,
    private val current: (() -> Boolean)?
) {
    enum class Phase { PENDING, NOT_IN_USE, UNAVAILABLE, PAUSED, CONNECTING, CONNECTED }

    val phase: Phase get() = if (current != null && !current.invoke()) Phase.PENDING else observedPhase
    val applied: Boolean get() = observedApplied && current?.invoke() == true

    /** The caller must separately retain its still-current durable selection receipt. */
    fun isCurrentFor(logicalNode: ChainNode): Boolean = node == logicalNode && current?.invoke() == true

    override fun toString(): String = "EndpointRuntimeStatus($phase)"
}
