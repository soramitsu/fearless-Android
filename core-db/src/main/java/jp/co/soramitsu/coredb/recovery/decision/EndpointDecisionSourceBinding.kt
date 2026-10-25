package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.EndpointRecordRef

/** Prepared exact typed source fingerprint; no text, bytes or authorization is retained. */
class EndpointDecisionSourceBinding internal constructor(
    val ref: EndpointRecordRef,
    internal val fingerprint: String
) {
    init { EndpointDecisionLimits.requireDigest(fingerprint) }
    /** Compare the exact source revision and typed fingerprint without exposing either raw payload. */
    fun matches(other: EndpointDecisionSourceBinding): Boolean = ref == other.ref && fingerprint == other.fingerprint

    override fun toString(): String = "EndpointDecisionSourceBinding(source=${ref.source}, rowId=${ref.rowId}, revision=${ref.revision})"
}
