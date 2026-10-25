package jp.co.soramitsu.coredb.recovery.decision

/** Captured trusted catalog identity; this choice confers no endpoint/network authority. */
class EndpointDecisionTarget internal constructor(
    val chainId: String,
    internal val identityFingerprint: String,
    val family: Family,
    val expectedGenesis: String?
) {
    enum class Family { SUBSTRATE, SOLANA, UNSUPPORTED }

    init {
        EndpointDecisionLimits.requireText(chainId, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true)
        EndpointDecisionLimits.requireDigest(identityFingerprint)
        require((family == Family.UNSUPPORTED) == (expectedGenesis == null))
        expectedGenesis?.let { EndpointDecisionLimits.requireText(it, 128, nonempty = true) }
    }

    override fun toString(): String = "EndpointDecisionTarget(family=$family)"
}
