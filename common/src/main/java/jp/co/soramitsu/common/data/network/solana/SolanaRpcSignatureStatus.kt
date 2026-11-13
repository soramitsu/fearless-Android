package jp.co.soramitsu.common.data.network.solana

/** A queried signature's observed chain result; absence is represented separately by null. */
data class SolanaRpcSignatureStatus(
    val slot: Long,
    val confirmationStatus: String?,
    val confirmations: Long?,
    val errorJson: String?
) {
    /**
     * Processed-only results cannot settle an ambiguous send or release its retained intent. Nodes that omit
     * `confirmationStatus` report a rooted transaction with `confirmations: null`; the parser requires both the
     * `confirmations` and `err` keys, so null here is a node's explicit answer, not a missing field.
     */
    val isConfirmed: Boolean
        get() = confirmationStatus in setOf("confirmed", "finalized") ||
            (confirmationStatus == null && (confirmations == null || confirmations > 0))
}
