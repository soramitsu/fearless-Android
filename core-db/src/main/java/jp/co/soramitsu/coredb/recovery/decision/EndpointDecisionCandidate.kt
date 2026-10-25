package jp.co.soramitsu.coredb.recovery.decision

/** User-chosen exact text; this neither normalizes a URL nor verifies a network identity. */
class EndpointDecisionCandidate(val name: String, val url: String) {
    init {
        EndpointDecisionLimits.requireText(name, EndpointDecisionLimits.LABEL_BYTES)
        EndpointDecisionLimits.requireText(url, EndpointDecisionLimits.URL_BYTES, nonempty = true)
    }

    override fun toString(): String = "EndpointDecisionCandidate"
}
