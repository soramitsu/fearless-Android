package jp.co.soramitsu.common.data.network.endpoint

/** Transient observation only; no outcome authorizes persistence, projection or selection. */
enum class EndpointProbeOutcome {
    MATCH, MISMATCH, MALFORMED_RESPONSE, TRANSPORT_FAILURE, RESPONSE_LIMIT, TIMED_OUT, BUSY
}
