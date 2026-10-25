package jp.co.soramitsu.account.impl.presentation.recovery

import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeOutcome

/** Current-screen observations only; none is a reusable verification or selection grant. */
enum class SavedEndpointAttachmentStatus {
    ADDED_INACTIVE, REATTACHED_INACTIVE, PRESENT_ACTIVE, PRESENT_INACTIVE,
    MISMATCH, INVALID_RESPONSE, UNREACHABLE, TOO_LARGE, TIMED_OUT, BUSY, FAILED;

    companion object {
        internal fun fromProbe(outcome: EndpointProbeOutcome): SavedEndpointAttachmentStatus = when (outcome) {
            EndpointProbeOutcome.MATCH -> error("A match alone is not an attachment result")
            EndpointProbeOutcome.MISMATCH -> MISMATCH
            EndpointProbeOutcome.MALFORMED_RESPONSE -> INVALID_RESPONSE
            EndpointProbeOutcome.TRANSPORT_FAILURE -> UNREACHABLE
            EndpointProbeOutcome.RESPONSE_LIMIT -> TOO_LARGE
            EndpointProbeOutcome.TIMED_OUT -> TIMED_OUT
            EndpointProbeOutcome.BUSY -> BUSY
        }
    }
}
